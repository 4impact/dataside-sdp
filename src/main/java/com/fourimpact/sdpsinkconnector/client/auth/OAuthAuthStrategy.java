package com.fourimpact.sdpsinkconnector.client.auth;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@ConditionalOnProperty(name = "sdp.auth.type", havingValue = "OAUTH2")
public class OAuthAuthStrategy implements AuthStrategy {

    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry;
    private final ReentrantLock lock = new ReentrantLock();

    public OAuthAuthStrategy(
            @Value("${sdp.auth.oauth2.token-url}") String tokenUrl,
            @Value("${sdp.auth.oauth2.client-id}") String clientId,
            @Value("${sdp.auth.oauth2.client-secret}") String clientSecret,
            @Value("${sdp.auth.oauth2.scope:SDPOnDemand.requests.ALL}") String scope) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.restClient = RestClient.create();
    }

    @Override
    public void apply(HttpHeaders headers) {
        headers.setBearerAuth(getValidToken());
    }

    private String getValidToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }
        lock.lock();
        try {
            // Double-check after acquiring lock
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }
            fetchToken();
        } finally {
            lock.unlock();
        }
        return cachedToken;
    }

    private void fetchToken() {
        log.info("Fetching new OAuth2 token from {}", tokenUrl);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("scope", scope);

        JsonNode response = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("access_token")) {
            throw new IllegalStateException("OAuth2 token response missing access_token");
        }

        cachedToken = response.get("access_token").asText();
        long expiresIn = response.has("expires_in") ? response.get("expires_in").asLong(3600) : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS);

        log.info("OAuth2 token acquired, expires in {}s (cached until {})", expiresIn, tokenExpiry);
    }
}
