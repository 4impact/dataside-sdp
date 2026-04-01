package com.fourimpact.sdpsinkconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fourimpact.sdpsinkconnector.client.auth.AuthStrategy;
import com.fourimpact.sdpsinkconnector.exception.PermanentSdpException;
import com.fourimpact.sdpsinkconnector.exception.TransientSdpException;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpAddNotePayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCloseRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpCreateRequestPayload;
import com.fourimpact.sdpsinkconnector.model.sdp.SdpRequestTemplate;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class ServiceDeskPlusClientImpl implements ServiceDeskPlusClient {

    private final RestClient restClient;
    private final AuthStrategy authStrategy;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String portal;
    private final String apiVersion;

    public ServiceDeskPlusClientImpl(
            AuthStrategy authStrategy,
            ObjectMapper objectMapper,
            @Value("${sdp.base-url}") String baseUrl,
            @Value("${sdp.portal}") String portal,
            @Value("${sdp.api-version:v3}") String apiVersion) {
        this.authStrategy = authStrategy;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.portal = portal;
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
        String url = buildUrl("/request");
        log.debug("POST {} - create request", url);
        try {
            String requestBody = buildInputData(payload);
            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.parseMediaType("application/vnd.manageengine.sdp.v3+json"))
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> isPermanent(status) && status.value() != 400, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> status.value() == 400, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (isSdpRateLimitError(body)) {
                            throw new TransientSdpException("Rate limited by SDP (error 4015)");
                        }
                        throw new PermanentSdpException("Permanent HTTP 400: " + body);
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
        String url = buildUrl("/request/" + requestId);
        log.debug("PUT {} - update request", url);
        try {
            String requestBody = buildInputData(payload);
            restClient.put()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.parseMediaType("application/vnd.manageengine.sdp.v3+json"))
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> isPermanent(status) && status.value() != 400, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> status.value() == 400, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (isSdpRateLimitError(body)) {
                            throw new TransientSdpException("Rate limited by SDP (error 4015)");
                        }
                        throw new PermanentSdpException("Permanent HTTP 400: " + body);
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
        String url = buildUrl("/request/" + requestId + "/notes");
        log.debug("POST {} - add note", url);
        try {
            String requestBody = buildInputData("request_note", payload);
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.parseMediaType("application/vnd.manageengine.sdp.v3+json"))
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> isPermanent(status) && status.value() != 400, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> status.value() == 400, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (isSdpRateLimitError(body)) {
                            throw new TransientSdpException("Rate limited by SDP (error 4015)");
                        }
                        throw new PermanentSdpException("Permanent HTTP 400: " + body);
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
        String url = buildUrl("/request/" + requestId);
        log.debug("PUT {} - close request", url);
        try {
            String requestBody = buildInputData(payload);
            restClient.put()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.parseMediaType("application/vnd.manageengine.sdp.v3+json"))
                    .headers(authStrategy::apply)
                    .body("input_data=" + requestBody)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> isPermanent(status) && status.value() != 400, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .onStatus(status -> status.value() == 400, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (isSdpRateLimitError(body)) {
                            throw new TransientSdpException("Rate limited by SDP (error 4015)");
                        }
                        throw new PermanentSdpException("Permanent HTTP 400: " + body);
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

    /**
     * Fetches a single SDP request template by ID.
     *
     * <h3>Prerequisites</h3>
     * <ol>
     *   <li><b>OAuth scope</b> — the Zoho OAuth token must be issued with at least
     *       {@code SDPOnDemand.setup.READ}. The {@code SDPOnDemand.requests.ALL} scope
     *       that covers request CRUD is <em>not</em> sufficient for this endpoint;
     *       SDP returns HTTP 401 if only that scope is present.</li>
     *   <li><b>How to include the scope</b> — add it to the {@code scope} parameter
     *       when generating the authorisation code:
     *       <pre>
     *         scope=SDPOnDemand.requests.ALL,SDPOnDemand.setup.READ
     *       </pre>
     *       then exchange the code for a token via:
     *       <pre>
     *         POST https://accounts.zoho.com.au/oauth/v2/token
     *           code=&lt;auth_code&gt;
     *           grant_type=authorization_code
     *           client_id=&lt;client_id&gt;
     *           client_secret=&lt;client_secret&gt;
     *           redirect_uri=&lt;redirect_uri&gt;
     *       </pre>
     *   </li>
     *   <li><b>URL format</b>
     *       <pre>GET {base-url}/app/{portal}/api/v3/request_templates/{templateId}</pre>
     *       Example:
     *       <pre>GET https://servicedeskplus.net.au/app/itdesk/api/v3/request_templates/7564000000278687</pre>
     *   </li>
     *   <li><b>Auth header</b>
     *       <pre>Authorization: Zoho-oauthtoken &lt;access_token&gt;</pre>
     *   </li>
     *   <li><b>Template ID</b> — use the {@code id} value from the
     *       {@code GET /requests} list response (e.g. {@code "7564000000278687"}).
     *       Do <em>not</em> prefix the ID with a colon ({@code :}) as shown in
     *       some API documentation examples — that is a placeholder convention,
     *       not part of the actual URL.</li>
     * </ol>
     *
     * <h3>Equivalent curl</h3>
     * <pre>
     * curl --location \
     *   'https://servicedeskplus.net.au/app/itdesk/api/v3/request_templates/7564000000278687' \
     *   --header 'Authorization: Zoho-oauthtoken &lt;access_token&gt;' \
     *   --header 'Accept: application/vnd.manageengine.sdp.v3+json' \
     *   --header 'Content-Type: application/x-www-form-urlencoded'
     * </pre>
     *
     * @param templateId the SDP internal template ID (e.g. {@code "7564000000278687"})
     * @return the parsed {@link SdpRequestTemplate}, or {@code null} if the response
     *         body cannot be parsed
     * @throws TransientSdpException on 5xx / connection errors (eligible for retry)
     * @throws PermanentSdpException on 4xx errors, including 401 when the OAuth
     *         token is missing the {@code SDPOnDemand.setup.READ} scope
     */
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
    public SdpRequestTemplate getRequestTemplate(String templateId) {
        String url = buildUrl("/request_templates/" + templateId);
        log.debug("GET {} - get request template", url);
        try {
            String responseBody = restClient.get()
                    .uri(url)
                    .accept(MediaType.parseMediaType("application/vnd.manageengine.sdp.v3+json"))
                    .headers(authStrategy::apply)
                    .retrieve()
                    .onStatus(this::isTransient, (req, res) -> {
                        throw new TransientSdpException("Transient HTTP " + res.getStatusCode());
                    })
                    .onStatus(this::isPermanent, (req, res) -> {
                        throw new PermanentSdpException("Permanent HTTP " + res.getStatusCode());
                    })
                    .body(String.class);

            return extractTemplate(responseBody);
        } catch (TransientSdpException | PermanentSdpException e) {
            throw e;
        } catch (RestClientException e) {
            throw new TransientSdpException("Connection error calling SDP getRequestTemplate", e);
        } catch (Exception e) {
            throw new PermanentSdpException("Unexpected error in getRequestTemplate: " + e.getMessage(), e);
        }
    }

    private SdpRequestTemplate extractTemplate(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode templateNode = root.path("request_template");
            if (templateNode.isMissingNode()) {
                log.warn("No 'request_template' field in SDP response: {}", responseBody);
                return null;
            }
            return objectMapper.treeToValue(templateNode, SdpRequestTemplate.class);
        } catch (Exception e) {
            log.warn("Failed to parse SDP request template response: {}", e.getMessage());
            return null;
        }
    }

    private String buildUrl(String path) {
        return baseUrl + "/app/" + portal + "/api/" + apiVersion + path;
    }

    private boolean isTransient(HttpStatusCode status) {
        int code = status.value();
        return code == 429 || code == 503 || code == 504 || code == 502 || code >= 500;
    }

    private boolean isPermanent(HttpStatusCode status) {
        int code = status.value();
        return code == 400 || code == 401 || code == 403 || code == 404 || code == 422;
    }

    private boolean isSdpRateLimitError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode statusCode = root.path("response_status").path("status_code");
            return !statusCode.isMissingNode() && statusCode.asInt() == 4015;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildInputData(Object payload) {
        return buildInputData("request", payload);
    }

    private String buildInputData(String wrapperKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(wrapperKey, payload));
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
