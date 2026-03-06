package com.fourimpact.sdpsinkconnector.model.sdp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SdpCreateRequestPayload {

    private String subject;
    private String description;
    private SdpNamedEntity priority;
    private SdpNamedEntity urgency;
    private SdpNamedEntity impact;
    private SdpNamedEntity category;
    private SdpNamedEntity subcategory;
    private SdpNamedEntity group;
    private SdpTechnician technician;
    private SdpNamedEntity mode;
    private SdpNamedEntity request_type;
    private SdpNamedEntity site;
    private SdpNamedEntity template;
    private SdpCustomer customer;
    private SdpRequester requester;
    private List<String> email_ids_to_notify;
    private Map<String, Object> udf_fields;
    private SdpResolution resolution;
    private String impact_details;
    private SdpNamedEntity sla;
    private SdpNamedEntity level;
    private SdpNamedEntity item;
    private Boolean is_fcr;
    private String status_change_comments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SdpResolution {
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SdpNamedEntity {
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SdpRequester {
        private String name;
        private String email_id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SdpTechnician {
        private String email_id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SdpCustomer {
        private String id;
    }
}
