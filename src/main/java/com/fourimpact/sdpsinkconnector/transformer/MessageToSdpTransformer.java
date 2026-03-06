package com.fourimpact.sdpsinkconnector.transformer;

import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.model.KafkaMessage;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpUpdateRequestPayload;
import org.springframework.stereotype.Component;

@Component
public class MessageToSdpTransformer {

    public SdpCreateRequestPayload toCreatePayload(KafkaMessage message) {
        if (message.getSubject() == null || message.getSubject().isBlank()) {
            throw new PermanentSdpException("CREATE operation requires a subject field");
        }

        SdpCreateRequestPayload.SdpRequester requester = null;
        if (message.getRequester() != null) {
            requester = SdpCreateRequestPayload.SdpRequester.builder()
                    .name(message.getRequester().getName())
                    .email_id(message.getRequester().getEmail())
                    .build();
        }

        return SdpCreateRequestPayload.builder()
                .subject(message.getSubject())
                .description(message.getDescription())
                .priority(namedEntity(message.getPriority()))
                .urgency(namedEntity(message.getUrgency()))
                .impact(namedEntity(message.getImpact()))
                .category(namedEntity(message.getCategory()))
                .subcategory(namedEntity(message.getSubCategory()))
                .group(namedEntity(message.getGroup()))
                .technician(emailEntity(message.getTechnician()))
                .mode(namedEntity(message.getMode()))
                .request_type(namedEntity(message.getRequestType()))
                .site(namedEntity(message.getSite()))
                .template(namedEntity(message.getTemplate()))
                .customer(customerEntity(message.getCustomer()))
                .requester(requester)
                .email_ids_to_notify(message.getEmailIdsToNotify())
                .udf_fields(message.getCustomFields())
                .resolution(resolutionEntity(message.getResolution()))
                .impact_details(message.getImpactDetails())
                .sla(namedEntity(message.getSla()))
                .level(namedEntity(message.getLevel()))
                .item(namedEntity(message.getItem()))
                .is_fcr(message.getIsFcr())
                .status_change_comments(message.getStatusChangeComments())
                .build();
    }

    public SdpUpdateRequestPayload toUpdatePayload(KafkaMessage message) {
        validateRequestId(message, "UPDATE");

        return SdpUpdateRequestPayload.builder()
                .subject(message.getSubject())
                .description(message.getDescription())
                .priority(updateNamedEntity(message.getPriority()))
                .urgency(updateNamedEntity(message.getUrgency()))
                .impact(updateNamedEntity(message.getImpact()))
                .category(updateNamedEntity(message.getCategory()))
                .subcategory(updateNamedEntity(message.getSubCategory()))
                .group(updateNamedEntity(message.getGroup()))
                .technician(updateEmailEntity(message.getTechnician()))
                .mode(updateNamedEntity(message.getMode()))
                .request_type(updateNamedEntity(message.getRequestType()))
                .site(updateNamedEntity(message.getSite()))
                .template(updateNamedEntity(message.getTemplate()))
                .customer(updateCustomerEntity(message.getCustomer()))
                .email_ids_to_notify(message.getEmailIdsToNotify())
                .udf_fields(message.getCustomFields())
                .update_reason(message.getUpdateReason())
                .status_change_comments(message.getStatusChangeComments())
                .resolution(updateResolutionEntity(message.getResolution()))
                .impact_details(message.getImpactDetails())
                .sla(updateNamedEntity(message.getSla()))
                .level(updateNamedEntity(message.getLevel()))
                .item(updateNamedEntity(message.getItem()))
                .is_fcr(message.getIsFcr())
                .build();
    }

    public SdpAddNotePayload toAddNotePayload(KafkaMessage message) {
        validateRequestId(message, "ADD_NOTE");
        if (message.getNote() == null || message.getNote().isBlank()) {
            throw new PermanentSdpException("ADD_NOTE operation requires a note field");
        }

        return SdpAddNotePayload.builder()
                .description(message.getNote())
                .show_to_requester(true)
                .build();
    }

    public SdpCloseRequestPayload toClosePayload(KafkaMessage message) {
        validateRequestId(message, "CLOSE");

        return SdpCloseRequestPayload.builder()
                .status(SdpCloseRequestPayload.SdpStatus.builder().name("Closed").build())
                .closure_code(SdpCloseRequestPayload.SdpCloseCode.builder()
                        .name(message.getStatus() != null ? message.getStatus() : "Resolved")
                        .build())
                .status_change_comments(message.getClosureComments())
                .build();
    }

    private void validateRequestId(KafkaMessage message, String operation) {
        if (message.getSdpRequestId() == null || message.getSdpRequestId().isBlank()) {
            throw new PermanentSdpException(
                    operation + " operation requires sdpRequestId but it was null or blank");
        }
    }

    private SdpCreateRequestPayload.SdpResolution resolutionEntity(String content) {
        if (content == null || content.isBlank()) return null;
        return SdpCreateRequestPayload.SdpResolution.builder().content(content).build();
    }

    private SdpUpdateRequestPayload.SdpResolution updateResolutionEntity(String content) {
        if (content == null || content.isBlank()) return null;
        return SdpUpdateRequestPayload.SdpResolution.builder().content(content).build();
    }

    private SdpCreateRequestPayload.SdpNamedEntity namedEntity(String name) {
        if (name == null || name.isBlank()) return null;
        return SdpCreateRequestPayload.SdpNamedEntity.builder().name(name).build();
    }

    private SdpCreateRequestPayload.SdpTechnician emailEntity(String email) {
        if (email == null || email.isBlank()) return null;
        return SdpCreateRequestPayload.SdpTechnician.builder().email_id(email).build();
    }

    private SdpCreateRequestPayload.SdpCustomer customerEntity(String id) {
        if (id == null || id.isBlank()) return null;
        return SdpCreateRequestPayload.SdpCustomer.builder().id(id).build();
    }

    private SdpUpdateRequestPayload.SdpNamedEntity updateNamedEntity(String name) {
        if (name == null || name.isBlank()) return null;
        return SdpUpdateRequestPayload.SdpNamedEntity.builder().name(name).build();
    }

    private SdpUpdateRequestPayload.SdpTechnician updateEmailEntity(String email) {
        if (email == null || email.isBlank()) return null;
        return SdpUpdateRequestPayload.SdpTechnician.builder().email_id(email).build();
    }

    private SdpUpdateRequestPayload.SdpCustomer updateCustomerEntity(String id) {
        if (id == null || id.isBlank()) return null;
        return SdpUpdateRequestPayload.SdpCustomer.builder().id(id).build();
    }
}
