package com.fourimpact.sdpsinkconnector.model.sdp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SdpUpdateRequestPayload {

    private String subject;
    private SdpDescription description;
    private SdpNamedEntity priority;
    private SdpNamedEntity category;
    private SdpNamedEntity subcategory;
    private Map<String, Object> udf_fields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SdpDescription {
        private String content_type;
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
}
