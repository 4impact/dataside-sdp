package com.fourimpact.sdpsinkconnector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaMessage {

    private String messageId;
    private OperationType operation;
    private String sdpRequestId;
    private String subject;
    private String description;
    private String priority;
    private String category;
    private String subCategory;
    private RequesterInfo requester;
    private String note;
    private String status;
    private Map<String, Object> customFields;
    private Instant timestamp;
}
