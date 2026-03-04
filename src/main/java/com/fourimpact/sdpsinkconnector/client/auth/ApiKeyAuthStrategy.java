package com.fourimpact.sdpsinkconnector.client.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sdp.auth.type", havingValue = "API_KEY", matchIfMissing = true)
public class ApiKeyAuthStrategy implements AuthStrategy {

    private final String apiKey;

    public ApiKeyAuthStrategy(
            @org.springframework.beans.factory.annotation.Value("${sdp.auth.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void apply(HttpHeaders headers) {
        headers.set("AUTHTOKEN", apiKey);
    }
}
