package com.fourimpact.sdpsinkconnector.transformer;

import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.model.KafkaMessage;
import com.fourimpact.sdpsinkconnector.model.OperationType;
import com.fourimpact.sdpsinkconnector.model.RequesterInfo;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpUpdateRequestPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageToSdpTransformerTest {

    private MessageToSdpTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new MessageToSdpTransformer();
    }

    // ---- CREATE ----

    @Test
    void toCreatePayload_mapsAllFields() {
        KafkaMessage msg = KafkaMessage.builder()
                .messageId("msg-1")
                .operation(OperationType.CREATE)
                .subject("VPN broken")
                .description("Cannot connect")
                .priority("High")
                .category("Network")
                .subCategory("VPN")
                .requester(RequesterInfo.builder().name("Jane Doe").email("jane@example.com").build())
                .build();

        SdpCreateRequestPayload payload = transformer.toCreatePayload(msg);

        assertThat(payload.getSubject().getSubject()).isEqualTo("VPN broken");
        assertThat(payload.getDescription().getContent()).isEqualTo("Cannot connect");
        assertThat(payload.getDescription().getContent_type()).isEqualTo("plaintext");
        assertThat(payload.getPriority().getName()).isEqualTo("High");
        assertThat(payload.getCategory().getName()).isEqualTo("Network");
        assertThat(payload.getSubcategory().getName()).isEqualTo("VPN");
        assertThat(payload.getRequester().getName()).isEqualTo("Jane Doe");
        assertThat(payload.getRequester().getEmail_id()).isEqualTo("jane@example.com");
    }

    @Test
    void toCreatePayload_throwsPermanentException_whenSubjectMissing() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.CREATE)
                .build();

        assertThatThrownBy(() -> transformer.toCreatePayload(msg))
                .isInstanceOf(PermanentSdpException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void toCreatePayload_nullOptionalFields_producesNullInPayload() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.CREATE)
                .subject("Only subject")
                .build();

        SdpCreateRequestPayload payload = transformer.toCreatePayload(msg);

        assertThat(payload.getPriority()).isNull();
        assertThat(payload.getCategory()).isNull();
        assertThat(payload.getRequester()).isNull();
        assertThat(payload.getDescription()).isNull();
    }

    // ---- UPDATE ----

    @Test
    void toUpdatePayload_mapsFields() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.UPDATE)
                .sdpRequestId("12345")
                .subject("Updated subject")
                .description("Updated description")
                .priority("Low")
                .build();

        SdpUpdateRequestPayload payload = transformer.toUpdatePayload(msg);

        assertThat(payload.getSubject()).isEqualTo("Updated subject");
        assertThat(payload.getDescription().getContent()).isEqualTo("Updated description");
        assertThat(payload.getPriority().getName()).isEqualTo("Low");
    }

    @Test
    void toUpdatePayload_throwsPermanentException_whenRequestIdMissing() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.UPDATE)
                .subject("Test")
                .build();

        assertThatThrownBy(() -> transformer.toUpdatePayload(msg))
                .isInstanceOf(PermanentSdpException.class)
                .hasMessageContaining("sdpRequestId");
    }

    // ---- ADD_NOTE ----

    @Test
    void toAddNotePayload_mapsNote() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.ADD_NOTE)
                .sdpRequestId("12345")
                .note("This is a note")
                .build();

        SdpAddNotePayload payload = transformer.toAddNotePayload(msg);

        assertThat(payload.getNote().getContent()).isEqualTo("This is a note");
        assertThat(payload.getNote().getShow_to_requester()).isTrue();
    }

    @Test
    void toAddNotePayload_throwsPermanentException_whenNoteBlank() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.ADD_NOTE)
                .sdpRequestId("12345")
                .note("")
                .build();

        assertThatThrownBy(() -> transformer.toAddNotePayload(msg))
                .isInstanceOf(PermanentSdpException.class)
                .hasMessageContaining("note");
    }

    // ---- CLOSE ----

    @Test
    void toClosePayload_setsClosedStatus() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.CLOSE)
                .sdpRequestId("12345")
                .status("Resolved")
                .build();

        SdpCloseRequestPayload payload = transformer.toClosePayload(msg);

        assertThat(payload.getStatus().getName()).isEqualTo("Closed");
        assertThat(payload.getClosure_code().getName()).isEqualTo("Resolved");
    }

    @Test
    void toClosePayload_usesDefaultClosureCode_whenStatusNull() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.CLOSE)
                .sdpRequestId("12345")
                .build();

        SdpCloseRequestPayload payload = transformer.toClosePayload(msg);

        assertThat(payload.getClosure_code().getName()).isEqualTo("Resolved");
    }

    @Test
    void toClosePayload_throwsPermanentException_whenRequestIdMissing() {
        KafkaMessage msg = KafkaMessage.builder()
                .operation(OperationType.CLOSE)
                .build();

        assertThatThrownBy(() -> transformer.toClosePayload(msg))
                .isInstanceOf(PermanentSdpException.class)
                .hasMessageContaining("sdpRequestId");
    }
}
