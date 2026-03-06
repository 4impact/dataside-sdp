package com.fourimpact.sdpsinkconnector.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourimpact.sdpsinkconnector.client.auth.AuthStrategy;
import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
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
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/requests"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"request\":{\"id\":\"REQ-999\"}}")));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject(SdpCreateRequestPayload.SdpSubject.builder().subject("Test").build())
                .build();

        String requestId = client.createRequest(payload);

        assertThat(requestId).isEqualTo("REQ-999");
    }

    @Test
    void createRequest_503_throwsTransientException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/requests"))
                .willReturn(aResponse().withStatus(503)));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject(SdpCreateRequestPayload.SdpSubject.builder().subject("Test").build())
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(TransientSdpException.class);
    }

    @Test
    void createRequest_400_throwsPermanentException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/requests"))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"bad request\"}")));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject(SdpCreateRequestPayload.SdpSubject.builder().subject("Test").build())
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void createRequest_404_throwsPermanentException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/requests"))
                .willReturn(aResponse().withStatus(404)));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject(SdpCreateRequestPayload.SdpSubject.builder().subject("Test").build())
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void createRequest_429_throwsTransientException() {
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/requests"))
                .willReturn(aResponse().withStatus(429)));

        SdpCreateRequestPayload payload = SdpCreateRequestPayload.builder()
                .subject(SdpCreateRequestPayload.SdpSubject.builder().subject("Test").build())
                .build();

        assertThatThrownBy(() -> client.createRequest(payload))
                .isInstanceOf(TransientSdpException.class);
    }

    // ---- updateRequest ----

    @Test
    void updateRequest_success() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        SdpUpdateRequestPayload payload = SdpUpdateRequestPayload.builder()
                .subject("Updated")
                .build();

        // Should not throw
        client.updateRequest("REQ-100", payload);

        wireMock.verify(putRequestedFor(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100")));
    }

    @Test
    void updateRequest_401_throwsPermanentException() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100"))
                .willReturn(aResponse().withStatus(401)));

        SdpUpdateRequestPayload payload = SdpUpdateRequestPayload.builder().build();

        assertThatThrownBy(() -> client.updateRequest("REQ-100", payload))
                .isInstanceOf(PermanentSdpException.class);
    }

    @Test
    void updateRequest_400_sdp4015_throwsTransientException() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/requests/123"))
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
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/requests/123"))
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
        wireMock.stubFor(post(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100/notes"))
                .willReturn(aResponse().withStatus(201).withBody("{}")));

        SdpAddNotePayload payload = SdpAddNotePayload.builder()
                .description("Test note")
                .show_to_requester(true)
                .build();

        client.addNote("REQ-100", payload);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100/notes")));
    }

    // ---- closeRequest ----

    @Test
    void closeRequest_success() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        SdpCloseRequestPayload payload = SdpCloseRequestPayload.builder()
                .status(SdpCloseRequestPayload.SdpStatus.builder().name("Closed").build())
                .build();

        client.closeRequest("REQ-100", payload);

        wireMock.verify(putRequestedFor(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100")));
    }

    @Test
    void closeRequest_504_throwsTransientException() {
        wireMock.stubFor(put(urlPathEqualTo("/app/test-portal/api/v3/requests/REQ-100"))
                .willReturn(aResponse().withStatus(504)));

        SdpCloseRequestPayload payload = SdpCloseRequestPayload.builder().build();

        assertThatThrownBy(() -> client.closeRequest("REQ-100", payload))
                .isInstanceOf(TransientSdpException.class);
    }
}
