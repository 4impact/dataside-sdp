package com.fourimpact.sdpsinkconnector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
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
    private String urgency;
    private String impact;
    private String category;
    private String subCategory;
    private String group;
    private String technician;
    private String mode;
    private String requestType;
    private String site;
    private String customer;
    private String template;
    private List<String> emailIdsToNotify;
    private RequesterInfo requester;
    private String note;
    private String status;
    private String updateReason;
    private String closureComments;
    private String resolution;
    private String impactDetails;
    private String sla;
    private String level;
    private String item;
    private Boolean isFcr;
    private String statusChangeComments;
    private Map<String, Object> customFields;
    private Instant timestamp;
}
