package com.fourimpact.sdpsinkconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourimpact.sdpsinkconnector.client.auth.AuthStrategy;
import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpUpdateRequestPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
public class ServiceDeskPlusClientImpl implements ServiceDeskPlusClient {

    private final RestClient restClient;
    private final AuthStrategy authStrategy;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiVersion;

    public ServiceDeskPlusClientImpl(
            AuthStrategy authStrategy,
            ObjectMapper objectMapper,
            @Value("${sdp.base-url}") String baseUrl,
            @Value("${sdp.api-version:v3}") String apiVersion) {
        this.authStrategy = authStrategy;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiVersion = apiVersion;
        this.restClient = RestClient.create();
    }

    @Override
    @Retryable(
            retryFor = TransientSdpException.class,
            maxAttemptsExpression = "${sdp.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${sdp.retry.initial-interval-ms:1000}",
                    multiplierExpression = "${sdp.retry.multiplier:2.0}",
                    maxDelayExpression = "${sdp.retry.max-interval-ms:15000}"
            )
    )
    public String createRequest(SdpCreateRequestPayload payload) {
        String url = buildUrl("/requests");
        log.debug("POST {} - create request", url);
        try {
            String requestBody = buildInputData(payload);
            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(this::isPermanent, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .body(String.class);

            return extractRequestId(responseBody);
        } catch (TransientSdpException | PermanentSdpException e) {
            throw e;
        } catch (RestClientException e) {
            throw new TransientSdpException("Connection error calling SDP createRequest", e);
        } catch (Exception e) {
            throw new PermanentSdpException("Unexpected error in createRequest: " + e.getMessage(), e);
        }
    }

    @Override
    @Retryable(
            retryFor = TransientSdpException.class,
            maxAttemptsExpression = "${sdp.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${sdp.retry.initial-interval-ms:1000}",
                    multiplierExpression = "${sdp.retry.multiplier:2.0}",
                    maxDelayExpression = "${sdp.retry.max-interval-ms:15000}"
            )
    )
    public void updateRequest(String requestId, SdpUpdateRequestPayload payload) {
        String url = buildUrl("/requests/" + requestId);
        log.debug("PUT {} - update request", url);
        try {
            String requestBody = buildInputData(payload);
            restClient.put()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(this::isPermanent, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (TransientSdpException | PermanentSdpException e) {
            throw e;
        } catch (RestClientException e) {
            throw new TransientSdpException("Connection error calling SDP updateRequest", e);
        } catch (Exception e) {
            throw new PermanentSdpException("Unexpected error in updateRequest: " + e.getMessage(), e);
        }
    }

    @Override
    @Retryable(
            retryFor = TransientSdpException.class,
            maxAttemptsExpression = "${sdp.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${sdp.retry.initial-interval-ms:1000}",
                    multiplierExpression = "${sdp.retry.multiplier:2.0}",
                    maxDelayExpression = "${sdp.retry.max-interval-ms:15000}"
            )
    )
    public void addNote(String requestId, SdpAddNotePayload payload) {
        String url = buildUrl("/requests/" + requestId + "/notes");
        log.debug("POST {} - add note", url);
        try {
            String requestBody = buildInputData(payload);
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(this::isPermanent, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (TransientSdpException | PermanentSdpException e) {
            throw e;
        } catch (RestClientException e) {
            throw new TransientSdpException("Connection error calling SDP addNote", e);
        } catch (Exception e) {
            throw new PermanentSdpException("Unexpected error in addNote: " + e.getMessage(), e);
        }
    }

    @Override
    @Retryable(
            retryFor = TransientSdpException.class,
            maxAttemptsExpression = "${sdp.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${sdp.retry.initial-interval-ms:1000}",
                    multiplierExpression = "${sdp.retry.multiplier:2.0}",
                    maxDelayExpression = "${sdp.retry.max-interval-ms:15000}"
            )
    )
    public void closeRequest(String requestId, SdpCloseRequestPayload payload) {
        String url = buildUrl("/requests/" + requestId + "/close");
        log.debug("POST {} - close request", url);
        try {
            String requestBody = buildInputData(payload);
            restClient.put()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(this::isPermanent, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (TransientSdpException | PermanentSdpException e) {
            throw e;
        } catch (RestClientException e) {
            throw new TransientSdpException("Connection error calling SDP closeRequest", e);
        } catch (Exception e) {
            throw new PermanentSdpException("Unexpected error in closeRequest: " + e.getMessage(), e);
        }
    }

    private String buildUrl(String path) {
        return baseUrl + "/api/" + apiVersion + path;
    }

    private boolean isTransient(HttpStatusCode status) {
        int code = status.value();
        return code == 429 || code == 503 || code == 504 || code == 502 || code >= 500;
    }

    private boolean isPermanent(HttpStatusCode status) {
        int code = status.value();
        return code == 400 || code == 401 || code == 403 || code == 404 || code == 422;
    }

    private String buildInputData(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("request", payload));
            return java.net.URLEncoder.encode(json, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PermanentSdpException("Failed to serialize SDP payload", e);
        }
    }

    private String extractRequestId(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode requestNode = root.path("request");
            if (requestNode.has("id")) {
                return requestNode.get("id").asText();
            }
            log.warn("Could not extract request id from SDP response: {}", responseBody);
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse SDP create response: {}", e.getMessage());
            return null;
        }
    }
}
