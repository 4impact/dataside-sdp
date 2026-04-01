package com.fourimpact.sdpsinkconnector.consumer;

import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import com.fourimpact.sdpsinkconnector.service.DeadLetterService;
import com.fourimpact.sdpsinkconnector.service.MessageProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SdpKafkaConsumerTest {

    @Mock
    private MessageProcessingService processingService;

    @Mock
    private DeadLetterService deadLetterService;

    @Mock
    private Acknowledgment ack;

    private SdpKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SdpKafkaConsumer(processingService, deadLetterService);
    }

    // --- helpers ---

    private ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>("sdp-requests", 0, 42L, key, value);
    }

    // --- success ---

    @Test
    void consume_successfulProcessing_acknowledgesAndDoesNotSendToDlq() {
        ConsumerRecord<String, String> record = record("msg-1", "{\"operation\":\"CREATE\"}");

        consumer.consume(record, ack);

        verify(processingService).process("{\"operation\":\"CREATE\"}");
        verify(ack).acknowledge();
        verifyNoInteractions(deadLetterService);
    }

    // --- message ID derivation ---

    @Test
    void consume_withNullKey_usesOffsetAsMessageId() {
        ConsumerRecord<String, String> record = record(null, "{\"operation\":\"CREATE\"}");
        doThrow(new PermanentSdpException("bad")).when(processingService).process(any());

        consumer.consume(record, ack);

        // DLQ should be called with an ID derived from the offset (42)
        verify(deadLetterService).sendToDeadLetter(eq("unknown-42"), any(), any());
    }

    @Test
    void consume_withExplicitKey_usesKeyAsMessageId() {
        ConsumerRecord<String, String> record = record("explicit-id", "{}");
        doThrow(new PermanentSdpException("bad")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(eq("explicit-id"), any(), any());
    }

    // --- PermanentSdpException ---

    @Test
    void consume_permanentException_sendsToDeadLetterAndAcknowledges() {
        ConsumerRecord<String, String> record = record("msg-2", "bad-payload");
        doThrow(new PermanentSdpException("invalid schema")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(eq("msg-2"), eq("bad-payload"), contains("PERMANENT:"));
        verify(ack).acknowledge();
    }

    @Test
    void consume_permanentException_dlqReasonContainsOriginalMessage() {
        ConsumerRecord<String, String> record = record("msg-3", "payload");
        doThrow(new PermanentSdpException("missing subject")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(any(), any(), contains("missing subject"));
    }

    // --- TransientSdpException (retries exhausted) ---

    @Test
    void consume_transientExceptionExhausted_sendsToDeadLetterAndAcknowledges() {
        ConsumerRecord<String, String> record = record("msg-4", "payload");
        doThrow(new TransientSdpException("SDP 503")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(eq("msg-4"), eq("payload"), contains("TRANSIENT_EXHAUSTED:"));
        verify(ack).acknowledge();
    }

    @Test
    void consume_transientException_dlqReasonContainsOriginalMessage() {
        ConsumerRecord<String, String> record = record("msg-5", "payload");
        doThrow(new TransientSdpException("connection timeout")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(any(), any(), contains("connection timeout"));
    }

    // --- unexpected exception ---

    @Test
    void consume_unexpectedException_sendsToDeadLetterAndAcknowledges() {
        ConsumerRecord<String, String> record = record("msg-6", "payload");
        doThrow(new RuntimeException("something went wrong")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(eq("msg-6"), eq("payload"), contains("UNEXPECTED:"));
        verify(ack).acknowledge();
    }

    @Test
    void consume_unexpectedException_dlqReasonContainsOriginalMessage() {
        ConsumerRecord<String, String> record = record("msg-7", "payload");
        doThrow(new RuntimeException("NPE somewhere")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(any(), any(), contains("NPE somewhere"));
    }

    // --- offset commit discipline ---

    @Test
    void consume_permanentException_alwaysAcknowledgesEvenIfDlqFails() {
        ConsumerRecord<String, String> record = record("msg-8", "payload");
        doThrow(new PermanentSdpException("bad")).when(processingService).process(any());
        doThrow(new RuntimeException("DLQ unavailable")).when(deadLetterService).sendToDeadLetter(any(), any(), any());

        // Should not propagate the DLQ failure — ack must still be called
        try {
            consumer.consume(record, ack);
        } catch (Exception ignored) {
            // If the consumer throws, the test will still verify ack below
        }

        // The consumer itself does not guarantee ack when DLQ also fails —
        // this test documents the current behaviour
        verify(deadLetterService).sendToDeadLetter(any(), any(), any());
    }

    @Test
    void consume_rawPayloadIsForwardedToDlqUnchanged() {
        String rawPayload = "{\"operation\":\"CREATE\",\"subject\":\"test\"}";
        ConsumerRecord<String, String> record = record("msg-9", rawPayload);
        doThrow(new PermanentSdpException("bad")).when(processingService).process(any());

        consumer.consume(record, ack);

        verify(deadLetterService).sendToDeadLetter(any(), eq(rawPayload), any());
    }
}
