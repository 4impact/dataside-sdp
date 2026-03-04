package com.fourimpact.sdpsinkconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sdp.topics.dlq}")
    private String dlqTopic;

    public void sendToDeadLetter(String messageId, String originalPayload, String errorReason) {
        try {
            Map<String, Object> dlqMessage = new LinkedHashMap<>();
            dlqMessage.put("messageId", messageId);
            dlqMessage.put("errorReason", errorReason);
            dlqMessage.put("failedAt", Instant.now().toString());
            dlqMessage.put("originalPayload", originalPayload);

            String dlqPayload = objectMapper.writeValueAsString(dlqMessage);
            kafkaTemplate.send(dlqTopic, messageId, dlqPayload);
            log.info("Message {} sent to DLQ topic '{}'. Reason: {}", messageId, dlqTopic, errorReason);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send message {} to DLQ: {}", messageId, e.getMessage(), e);
        }
    }
}
