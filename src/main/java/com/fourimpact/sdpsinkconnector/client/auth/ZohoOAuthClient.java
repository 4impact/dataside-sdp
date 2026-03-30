package com.fourimpact.sdpsinkconnector.client.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the four Zoho OAuth 2.0 token operations used by SDP Cloud.
 *
 * <p>All four flows post to {@code accounts-server-url/oauth/v2/token} (or the
 * {@code /revoke} sub-path) with {@code application/x-www-form-urlencoded} bodies.
 * See <a href="https://www.zoho.com/accounts/protocol/oauth.html">Zoho OAuth docs</a>.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sdp.auth.type", havingValue = "OAUTH2")
public class ZohoOAuthClient {

    private final String tokenUrl;
    private final String revokeUrl;
    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;

    public ZohoOAuthClient(
            @Value("${sdp.auth.oauth2.token-url}") String tokenUrl,
            @Value("${sdp.auth.oauth2.revoke-url}") String revokeUrl,
            @Value("${sdp.auth.oauth2.client-id}") String clientId,
            @Value("${sdp.auth.oauth2.client-secret}") String clientSecret) {
        this.tokenUrl = tokenUrl;
        this.revokeUrl = revokeUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.create();
        log.info("ZohoOAuthClient initialised — token-url={}", tokenUrl);
    }

    /**
     * Authorization Code flow — exchanges a one-time {@code code} for an access token
     * and a refresh token.
     *
     * <pre>
     * POST {token-url}
     * grant_type=authorization_code
     * &amp;code={code}
     * &amp;client_id={clientId}
     * &amp;client_secret={clientSecret}
     * &amp;redirect_uri={redirectUri}
     * </pre>
     */
    public OAuthTokenResponse generateTokenFromAuthCode(String code, String redirectUri) {
        log.info("Exchanging authorization code for access + refresh tokens");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        return post(tokenUrl, form);
    }

    /**
     * Refresh Token flow — exchanges a {@code refreshToken} for a new access token.
     *
     * <pre>
     * POST {token-url}
     * grant_type=refresh_token
     * &amp;refresh_token={refreshToken}
     * &amp;client_id={clientId}
     * &amp;client_secret={clientSecret}
     * </pre>
     */
    public OAuthTokenResponse refreshAccessToken(String refreshToken) {
        log.info("Refreshing access token using refresh token");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return post(tokenUrl, form);
    }

    /**
     * Client Credentials flow — obtains an access token with no user context.
     * Note: Zoho does not return a refresh token for this grant type.
     *
     * <pre>
     * POST {token-url}
     * grant_type=client_credentials
     * &amp;client_id={clientId}
     * &amp;client_secret={clientSecret}
     * &amp;scope={scope}
     * </pre>
     */
    public OAuthTokenResponse generateTokenWithClientCredentials(String scope) {
        log.info("Fetching OAuth2 token via client_credentials, scope={}", scope);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", scope);
        return post(tokenUrl, form);
    }

    /**
     * Revoke Token — invalidates an access token or refresh token.
     * Revoking a refresh token also invalidates all its associated access tokens.
     *
     * <pre>
     * POST {revoke-url}?token={token}
     * </pre>
     */
    public void revokeToken(String token) {
        log.info("Revoking OAuth2 token");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        restClient.post()
                .uri(revokeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
        log.info("OAuth2 token revoked successfully");
    }

    private OAuthTokenResponse post(String url, MultiValueMap<String, String> form) {
        OAuthTokenResponse response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(OAuthTokenResponse.class);

        if (response == null || response.getAccessToken() == null) {
            throw new IllegalStateException("Zoho OAuth response is missing access_token");
        }
        return response;
    }
}
