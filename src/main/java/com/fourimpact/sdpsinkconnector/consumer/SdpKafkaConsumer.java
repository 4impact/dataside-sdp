package com.fourimpact.sdpsinkconnector.consumer;

import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import com.fourimpact.sdpsinkconnector.service.DeadLetterService;
import com.fourimpact.sdpsinkconnector.service.MessageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SdpKafkaConsumer {

    private final MessageProcessingService processingService;
    private final DeadLetterService deadLetterService;

    @KafkaListener(topics = "${sdp.topics.input}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String rawValue = record.value();
        String messageId = record.key() != null ? record.key() : "unknown-" + record.offset();

        log.info("Received message from topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        try {
            processingService.process(rawValue);
            ack.acknowledge();
        } catch (PermanentSdpException e) {
            log.error("Permanent error processing message [{}]: {}", messageId, e.getMessage(), e);
            deadLetterService.sendToDeadLetter(messageId, rawValue, "PERMANENT: " + e.getMessage());
            ack.acknowledge();
        } catch (TransientSdpException e) {
            // All retries exhausted (Spring Retry wrapped it)
            log.error("Transient error exhausted retries for message [{}]: {}", messageId, e.getMessage(), e);
            deadLetterService.sendToDeadLetter(messageId, rawValue, "TRANSIENT_EXHAUSTED: " + e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Unexpected error processing message [{}]: {}", messageId, e.getMessage(), e);
            deadLetterService.sendToDeadLetter(messageId, rawValue, "UNEXPECTED: " + e.getMessage());
            ack.acknowledge();
        }
    }
}
