package com.fourimpact.sdpsinkconnector.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourimpact.sdpsinkconnector.client.auth.AuthStrategy;
import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpRequestTemplate;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpUpdateRequestPayload;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceDeskPlusClientImplTest {

    private WireMockServer wireMock;
    private ServiceDeskPlusClientImpl client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        AuthStrategy noopAuth = headers -> headers.set("AUTHTOKEN", "test-key");

        client = new ServiceDeskPlusClientImpl(
                noopAuth,
                objectMapper,
                "http://localhost:" + wireMock.port(),
                "test-portal",
                "v3"
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // ---- createRequest ----

    @Test
    void createRequest_success_returnsRequestId() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/request"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"request\":{\"id\":\"REQ-999\"}}")));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject("Test")
                .build();

        String requestId = client.createRequest(payload);

        assertThat(requestId).isEqualTo("REQ-999");
    }

    @Test
    void createRequest_503_throwsTransientException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/request"))
                .willReturn(aResponse().withStatus(503)));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject("Test")
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(TransientSdpException.class);
    }

    @Test
    void createRequest_400_throwsPermanentException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/request"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject("Test")
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void createRequest_404_throwsPermanentException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/request"))
                .willReturn(aResponse().withStatus(404)));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject("Test")
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void createRequest_429_throwsTransientException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/request"))
                .willReturn(aResponse().withStatus(429)));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject("Test")
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(TransientSdpException.class);
    }

    // ---- updateRequest ----

    @Test
    void updateRequest_success() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        SdpUpdateRequestPayload payload = SdpUpdateRequestPayload.builder()
                .subject("Updated")
                .build();

        // Should not throw
        client.updateRequest("REQ-100", payload);

        wireMock.verify(putRequestedFor(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100")));
    }

    @Test
    void updateRequest_401_throwsPermanentException() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100"))
                .willReturn(aResponse().withStatus(401)));

        SdpUpdateRequestPayload payload = SdpUpdateRequestPayload.builder().build();

        assertThatThrownBy(() -> client.updateRequest("REQ-100", payload))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void updateRequest_400_sdp4015_throwsTransientException() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/request/123"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response_status\":{\"status_code\":4015,\"status\":\"failed\"}}")));

        SdpUpdateRequestPayload payload = SdpUpdateRequestPayload.builder().build();

        assertThatThrownBy(() -> client.updateRequest("123", payload))
                .isInstanceOf(TransientSdpException.class)
                .hasMessageContaining("4015");
    }

    @Test
    void updateRequest_400_nonRateLimit_throwsPermanentException() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/request/123"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response_status\":{\"status_code\":4000,\"status\":\"failed\"}}")));

        SdpUpdateRequestPayload payload = SdpUpdateRequestPayload.builder().build();

        assertThatThrownBy(() -> client.updateRequest("123", payload))
                .isInstanceOf(PermanentSdpException.class);
    }

    // ---- addNote ----

    @Test
    void addNote_success() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100/notes"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        SdpAddNotePayload payload = SdpAddNotePayload.builder()
                .description("Test note")
                .show_to_requester(true)
                .build();

        client.addNote("REQ-100", payload);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100/notes")));
    }

    // ---- closeRequest ----

    @Test
    void closeRequest_success() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        SdpCloseRequestPayload payload = SdpCloseRequestPayload.builder()
                .status(SdpCloseRequestPayload.SdpStatus.builder().name("Closed").build())
                .build();

        client.closeRequest("REQ-100", payload);

        wireMock.verify(putRequestedFor(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100")));
    }

    @Test
    void closeRequest_504_throwsTransientException() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/request/REQ-100"))
                .willReturn(aResponse().withStatus(504)));

        SdpCloseRequestPayload payload = SdpCloseRequestPayload.builder().build();

        assertThatThrownBy(() -> client.closeRequest("REQ-100", payload))
                .isInstanceOf(TransientSdpException.class);
    }

    // ---- getRequestTemplate ----

    private static final String TEMPLATE_RESPONSE = """
            {
              "response_status": {"status_code": 2000, "status": "success"},
              "request_template": {
                "id": "7564000000278687",
                "name": "Default Request",
                "image": "incident-icon",
                "is_default": true,
                "is_service_template": false,
                "is_customer_segmented": false,
                "inactive": false,
                "comments": "Default template used for new request creation.",
                "request": {
                  "status": {"id": "7564000000004937", "name": "Open"},
                  "priority": null,
                  "subject": null
                }
              }
            }
            """;

    @Test
    void getRequestTemplate_success_returnsMappedTemplate() {
        wireMock.stubFor(get(urlPathEqualTo("/app/test-portal/api/v3/request_templates/TMPL-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(TEMPLATE_RESPONSE)));

        SdpRequestTemplate result = client.getRequestTemplate("TMPL-001");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("7564000000278687");
        assertThat(result.getName()).isEqualTo("Default Request");
        assertThat(result.isDefault()).isTrue();
        assertThat(result.isServiceTemplate()).isFalse();
        assertThat(result.isInactive()).isFalse();
        assertThat(result.getComments()).isEqualTo("Default template used for new request creation.");
        assertThat(result.getRequest()).isNotNull();
        assertThat(result.getRequest().getStatus().getName()).isEqualTo("Open");
    }

    @Test
    void getRequestTemplate_success_usesCorrectUrl() {
        wireMock.stubFor(get(urlPathEqualTo("/app/test-portal/api/v3/request_templates/TMPL-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(TEMPLATE_RESPONSE)));

        client.getRequestTemplate("TMPL-001");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/app/test-portal/api/v3/request_templates/TMPL-001")));
    }

    @Test
    void getRequestTemplate_missingTemplateNode_returnsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/app/test-portal/api/v3/request_templates/TMPL-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"response_status\":{\"status_code\":2000,\"status\":\"success\"}}")));

        SdpRequestTemplate result = client.getRequestTemplate("TMPL-001");

        assertThat(result).isNull();
    }

    @Test
    void getRequestTemplate_401_throwsPermanentException() {
        // 401 is the expected error when the OAuth token is missing SDPOnDemand.setup.READ scope
        wireMock.stubFor(get(urlPathEqualTo("/app/test-portal/api/v3/request_templates/TMPL-001"))
                .willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client.getRequestTemplate("TMPL-001"))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void getRequestTemplate_404_throwsPermanentException() {
        wireMock.stubFor(get(urlPathEqualTo("/app/test-portal/api/v3/request_templates/TMPL-001"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.getRequestTemplate("TMPL-001"))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void getRequestTemplate_503_throwsTransientException() {
        wireMock.stubFor(get(urlPathEqualTo("/app/test-portal/api/v3/request_templates/TMPL-001"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.getRequestTemplate("TMPL-001"))
                .isInstanceOf(TransientSdpException.class);
    }
}
