package com.fourimpact.sdpsinkconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fourimpact.sdpsinkconnector.client.ServiceDeskPlusClient;
import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.transformer.MessageToSdpTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageProcessingServiceTest {

    @Mock
    private ServiceDeskPlusClient sdpClient;

    private MessageProcessingService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MessageToSdpTransformer transformer = new MessageToSdpTransformer();
        service = new MessageProcessingService(objectMapper, transformer, sdpClient);
    }

    @Test
    void process_create_callsSdpCreate() {
        String json = """
                {
                  "messageId": "msg-001",
                  "operation": "CREATE",
                  "subject": "VPN broken",
                  "description": "Cannot connect"
                }
                """;

        when(sdpClient.createRequest(any())).thenReturn("REQ-123");

        service.process(json);

        verify(sdpClient).createRequest(any(SdpCreateRequestPayload.class));
    }

    @Test
    void process_update_callsSdpUpdate() {
        String json = """
                {
                  "messageId": "msg-002",
                  "operation": "UPDATE",
                  "sdpRequestId": "REQ-100",
                  "subject": "Updated subject"
                }
                """;

        service.process(json);

        verify(sdpClient).updateRequest(eq("REQ-100"), any());
    }

    @Test
    void process_addNote_callsSdpAddNote() {
        String json = """
                {
                  "messageId": "msg-003",
                  "operation": "ADD_NOTE",
                  "sdpRequestId": "REQ-100",
                  "note": "This is a note"
                }
                """;

        service.process(json);

        verify(sdpClient).addNote(eq("REQ-100"), any());
    }

    @Test
    void process_close_callsSdpClose() {
        String json = """
                {
                  "messageId": "msg-004",
                  "operation": "CLOSE",
                  "sdpRequestId": "REQ-100"
                }
                """;

        service.process(json);

        verify(sdpClient).closeRequest(eq("REQ-100"), any());
    }

    @Test
    void process_invalidJson_throwsPermanentException() {
        assertThatThrownBy(() -> service.process("not-json"))
                .isInstanceOf(PermanentSdpException.class)
                .hasMessageContaining("deserialize");
    }

    @Test
    void process_missingOperation_throwsPermanentException() {
        String json = """
                {
                  "messageId": "msg-005",
                  "subject": "No operation"
                }
                """;

        assertThatThrownBy(() -> service.process(json))
                .isInstanceOf(PermanentSdpException.class)
                .hasMessageContaining("operation");
    }

    @Test
    void process_createMissingSubject_throwsPermanentException() {
        String json = """
                {
                  "messageId": "msg-006",
                  "operation": "CREATE"
                }
                """;

        assertThatThrownBy(() -> service.process(json))
                .isInstanceOf(PermanentSdpException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void process_propagatesTransientException() {
        String json = """
                {
                  "messageId": "msg-007",
                  "operation": "CREATE",
                  "subject": "Test"
                }
                """;

        when(sdpClient.createRequest(any())).thenThrow(new TransientSdpException("503 Service Unavailable"));

        assertThatThrownBy(() -> service.process(json))
                .isInstanceOf(TransientSdpException.class);
    }
}
