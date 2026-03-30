package com.fourimpact.sdpsinkconnector.client.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OAuth 2.0 {@link AuthStrategy} for SDP Cloud (Zoho-backed).
 *
 * <p>Supports three grant types controlled by {@code sdp.auth.oauth2.grant-type}:
 * <ul>
 *   <li>{@code client_credentials} (default) — machine-to-machine; no refresh token</li>
 *   <li>{@code authorization_code} — exchanges a one-time auth code for tokens on first
 *       call, then uses the stored refresh token for all subsequent renewals</li>
 *   <li>{@code refresh_token} — uses a pre-configured refresh token directly</li>
 * </ul>
 *
 * <p>Token caching uses double-checked locking so concurrent callers never trigger
 * a redundant fetch.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sdp.auth.type", havingValue = "OAUTH2")
public class OAuthAuthStrategy implements AuthStrategy {

    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final ZohoOAuthClient oAuthClient;
    private final String grantType;
    private final String scope;
    private final String authorizationCode;
    private final String redirectUri;
    private final String configuredRefreshToken;

    private volatile String cachedAccessToken;
    private volatile String cachedRefreshToken;
    private volatile Instant tokenExpiry;
    private final ReentrantLock lock = new ReentrantLock();

    public OAuthAuthStrategy(
            ZohoOAuthClient oAuthClient,
            @Value("${sdp.auth.oauth2.grant-type:client_credentials}") String grantType,
            @Value("${sdp.auth.oauth2.scope:SDPOnDemand.requests.ALL}") String scope,
            @Value("${sdp.auth.oauth2.authorization-code:}") String authorizationCode,
            @Value("${sdp.auth.oauth2.redirect-uri:}") String redirectUri,
            @Value("${sdp.auth.oauth2.refresh-token:}") String configuredRefreshToken) {
        this.oAuthClient = oAuthClient;
        this.grantType = grantType;
        this.scope = scope;
        this.authorizationCode = authorizationCode;
        this.redirectUri = redirectUri;
        this.configuredRefreshToken = configuredRefreshToken;
        log.info("OAuthAuthStrategy initialised — grant-type={}", grantType);
    }

    @Override
    public void apply(HttpHeaders headers) {
        headers.setBearerAuth(getValidToken());
    }

    /**
     * Revokes the given token at the Zoho accounts server and clears the local cache
     * if it matches the currently held access token.
     */
    public void revokeToken(String token) {
        oAuthClient.revokeToken(token);
        lock.lock();
        try {
            if (token.equals(cachedAccessToken)) {
                cachedAccessToken = null;
                tokenExpiry = null;
            }
        } finally {
            lock.unlock();
        }
    }

    // --- internal token management ---

    private String getValidToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedAccessToken;
        }
        lock.lock();
        try {
            // Double-check after acquiring lock
            if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedAccessToken;
            }
            fetchToken();
        } finally {
            lock.unlock();
        }
        return cachedAccessToken;
    }

    private void fetchToken() {
        OAuthTokenResponse response = switch (grantType) {
            case "authorization_code" -> fetchViaAuthorizationCode();
            case "refresh_token" -> fetchViaRefreshToken();
            default -> oAuthClient.generateTokenWithClientCredentials(scope);
        };
        cacheResponse(response);
    }

    private OAuthTokenResponse fetchViaAuthorizationCode() {
        String refreshToken = effectiveRefreshToken();
        if (refreshToken != null) {
            return oAuthClient.refreshAccessToken(refreshToken);
        }
        // First call: exchange the one-time authorization code
        return oAuthClient.generateTokenFromAuthCode(authorizationCode, redirectUri);
    }

    private OAuthTokenResponse fetchViaRefreshToken() {
        String refreshToken = effectiveRefreshToken();
        if (refreshToken == null) {
            throw new IllegalStateException(
                    "grant-type=refresh_token but no refresh token is configured or cached");
        }
        return oAuthClient.refreshAccessToken(refreshToken);
    }

    /** Returns the cached refresh token if present, otherwise the one from config. */
    private String effectiveRefreshToken() {
        if (cachedRefreshToken != null && !cachedRefreshToken.isBlank()) {
            return cachedRefreshToken;
        }
        return (configuredRefreshToken != null && !configuredRefreshToken.isBlank())
                ? configuredRefreshToken
                : null;
    }

    private void cacheResponse(OAuthTokenResponse response) {
        cachedAccessToken = response.getAccessToken();
        if (response.getRefreshToken() != null) {
            cachedRefreshToken = response.getRefreshToken();
        }
        long expiresIn = response.getExpiresIn() > 0 ? response.getExpiresIn() : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS);
        log.info("OAuth2 token acquired, expires in {}s (cached until {})", expiresIn, tokenExpiry);
    }
}
