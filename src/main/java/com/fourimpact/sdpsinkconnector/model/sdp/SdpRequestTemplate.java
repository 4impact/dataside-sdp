package com.fourimpact.sdpsinkconnector.model.sdp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a ServiceDesk Plus request template returned by the
 * {@code GET /app/{portal}/api/v3/request_templates/{id}} endpoint.
 *
 * <p>Only the fields most relevant to request creation and routing are mapped here.
 * All other fields in the SDP response are silently ignored via
 * {@link JsonIgnoreProperties}.</p>
 *
 * <p><b>Prerequisites for fetching templates — see
 * {@link com.fourimpact.sdpsinkconnector.client.ServiceDeskPlusClientImpl#getRequestTemplate}</b></p>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SdpRequestTemplate {

    /** Internal SDP ID for this template (e.g. {@code "7564000000278687"}). */
    private String id;

    /** Display name shown in the SDP UI (e.g. {@code "Default Request"}). */
    private String name;

    /** Icon image key (e.g. {@code "incident-icon"}, {@code "mobile"}). */
    private String image;

    /** {@code true} if this is the fallback template used when no template is specified. */
    @JsonProperty("is_default")
    private boolean isDefault;

    /** {@code true} for service catalog templates; {@code false} for incident templates. */
    @JsonProperty("is_service_template")
    private boolean isServiceTemplate;

    /** {@code true} if this template is segmented per customer (MSP mode). */
    @JsonProperty("is_customer_segmented")
    private boolean isCustomerSegmented;

    /** {@code true} if this template has been disabled in SDP. */
    private boolean inactive;

    /** Admin-provided description/comments for this template. */
    private String comments;

    /** Default field values pre-filled when a request is created with this template. */
    private RequestDefaults request;

    /**
     * Default field values that SDP pre-populates on a new request
     * created from this template.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequestDefaults {

        private NamedEntity status;
        private NamedEntity priority;
        private NamedEntity urgency;
        private NamedEntity impact;
        private NamedEntity category;
        private NamedEntity subcategory;
        private NamedEntity group;
        private NamedEntity site;
        private String subject;
        private String description;

        @JsonProperty("email_ids_to_notify")
        private List<String> emailIdsToNotify;
    }

    /** A minimal SDP reference object that carries only a {@code name} field. */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NamedEntity {
        private String id;
        private String name;
    }
}
