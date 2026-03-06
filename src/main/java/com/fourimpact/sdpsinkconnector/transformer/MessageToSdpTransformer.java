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

        SdpCreateRequestPayload.SdpDescription description = null;
        if (message.getDescription() != null) {
            description = SdpCreateRequestPayload.SdpDescription.builder()
                    .content_type("plaintext")
                    .content(message.getDescription())
                    .build();
        }

        SdpCreateRequestPayload.SdpNamedEntity priority = namedEntity(message.getPriority());
        SdpCreateRequestPayload.SdpNamedEntity category = namedEntity(message.getCategory());
        SdpCreateRequestPayload.SdpNamedEntity subcategory = namedEntity(message.getSubCategory());

        return SdpCreateRequestPayload.builder()
                .subject(SdpCreateRequestPayload.SdpSubject.builder()
                        .subject(message.getSubject())
                        .build())
                .description(description)
                .priority(priority)
                .category(category)
                .subcategory(subcategory)
                .requester(requester)
                .udf_fields(message.getCustomFields())
                .build();
    }

    public SdpUpdateRequestPayload toUpdatePayload(KafkaMessage message) {
        validateRequestId(message, "UPDATE");

        SdpUpdateRequestPayload.SdpDescription description = null;
        if (message.getDescription() != null) {
            description = SdpUpdateRequestPayload.SdpDescription.builder()
                    .content_type("plaintext")
                    .content(message.getDescription())
                    .build();
        }

        SdpUpdateRequestPayload.SdpNamedEntity priority = updateNamedEntity(message.getPriority());
        SdpUpdateRequestPayload.SdpNamedEntity category = updateNamedEntity(message.getCategory());
        SdpUpdateRequestPayload.SdpNamedEntity subcategory = updateNamedEntity(message.getSubCategory());

        return SdpUpdateRequestPayload.builder()
                .subject(message.getSubject())
                .description(description)
                .priority(priority)
                .category(category)
                .subcategory(subcategory)
                .udf_fields(message.getCustomFields())
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
                .build();
    }

    private void validateRequestId(KafkaMessage message, String operation) {
        if (message.getSdpRequestId() == null || message.getSdpRequestId().isBlank()) {
            throw new PermanentSdpException(
                    operation + " operation requires sdpRequestId but it was null or blank");
        }
    }

    private SdpCreateRequestPayload.SdpNamedEntity namedEntity(String name) {
        if (name == null || name.isBlank()) return null;
        return SdpCreateRequestPayload.SdpNamedEntity.builder().name(name).build();
    }

    private SdpUpdateRequestPayload.SdpNamedEntity updateNamedEntity(String name) {
        if (name == null || name.isBlank()) return null;
        return SdpUpdateRequestPayload.SdpNamedEntity.builder().name(name).build();
    }
}
