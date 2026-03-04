package com.fourimpact.sdpsinkconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourimpact.sdpsinkconnector.client.ServiceDeskPlusClient;
import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.model.KafkaMessage;
import com.fourimpact.sdpsinkconnector.model.OperationType;
import com.fourimpact.sdpsinkconnector.transformer.MessageToSdpTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProcessingService {

    private final ObjectMapper objectMapper;
    private final MessageToSdpTransformer transformer;
    private final ServiceDeskPlusClient sdpClient;

    public void process(String rawJson) {
        KafkaMessage message = deserialize(rawJson);
        try {
            MDC.put("messageId", message.getMessageId());
            MDC.put("operation", String.valueOf(message.getOperation()));
            MDC.put("sdpRequestId", message.getSdpRequestId());

            log.info("Processing message: operation={}, sdpRequestId={}",
                    message.getOperation(), message.getSdpRequestId());

            if (message.getOperation() == null) {
                throw new PermanentSdpException("Message is missing required 'operation' field");
            }

            route(message);

            log.info("Message processed successfully");
        } finally {
            MDC.remove("messageId");
            MDC.remove("operation");
            MDC.remove("sdpRequestId");
        }
    }

    private KafkaMessage deserialize(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, KafkaMessage.class);
        } catch (Exception e) {
            // Use a placeholder messageId since we can't parse it
            throw new PermanentSdpException("Failed to deserialize Kafka message: " + e.getMessage(), e);
        }
    }

    private void route(KafkaMessage message) {
        OperationType op = message.getOperation();
        switch (op) {
            case CREATE -> {
                String requestId = sdpClient.createRequest(transformer.toCreatePayload(message));
                log.info("SDP request created with id={}", requestId);
            }
            case UPDATE -> sdpClient.updateRequest(message.getSdpRequestId(), transformer.toUpdatePayload(message));
            case ADD_NOTE -> sdpClient.addNote(message.getSdpRequestId(), transformer.toAddNotePayload(message));
            case CLOSE -> sdpClient.closeRequest(message.getSdpRequestId(), transformer.toClosePayload(message));
            default -> throw new PermanentSdpException("Unknown operation type: " + op);
        }
    }
}
