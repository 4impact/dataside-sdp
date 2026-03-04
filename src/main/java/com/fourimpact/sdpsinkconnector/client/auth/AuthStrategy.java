package com.fourimpact.sdpsinkconnector.client.auth;

import org.springframework.http.HttpHeaders;

/**
 * Strategy interface for applying authentication to outbound SDP API requests.
 */
public interface AuthStrategy {

    void apply(HttpHeaders headers);
}
