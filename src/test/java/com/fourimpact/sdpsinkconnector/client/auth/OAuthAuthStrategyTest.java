package com.fourimpact.sdpsinkconnector.client.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthAuthStrategyTest {

    @Mock
    private ZohoOAuthClient oAuthClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---- helpers ----

    /** Builds an OAuthTokenResponse by deserialising JSON so private fields are set. */
    private static OAuthTokenResponse token(String accessToken, String refreshToken, long expiresIn) {
        try {
            String json = String.format(
                    "{\"access_token\":\"%s\",\"refresh_token\":%s,\"expires_in\":%d,\"token_type\":\"Bearer\"}",
                    accessToken,
                    refreshToken == null ? "null" : "\"" + refreshToken + "\"",
                    expiresIn
            );
            return MAPPER.readValue(json, OAuthTokenResponse.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Token that lives for a long time — will be cached. */
    private static OAuthTokenResponse validToken(String accessToken) {
        return token(accessToken, null, 3600);
    }

    /** Token with expires_in=1 — after the 60s buffer is subtracted the expiry is already in
     *  the past, so the strategy treats it as expired immediately. */
    private static OAuthTokenResponse expiredToken(String accessToken) {
        return token(accessToken, null, 1);
    }

    private OAuthAuthStrategy strategy(String grantType, String authCode, String redirectUri, String refreshToken) {
        return new OAuthAuthStrategy(oAuthClient, grantType,
                "SDPOnDemand.requests.ALL,SDPOnDemand.setup.READ",
                authCode, redirectUri, refreshToken);
    }

    // ---- client_credentials ----

    @Test
    void apply_clientCredentials_setsBearer​AuthHeader() {
        when(oAuthClient.generateTokenWithClientCredentials(any())).thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("client_credentials", "", "", "");
        HttpHeaders headers = new HttpHeaders();

        s.apply(headers);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-abc");
    }

    @Test
    void apply_clientCredentials_callsClientCredentialsFlowWithConfiguredScope() {
        when(oAuthClient.generateTokenWithClientCredentials(any())).thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("client_credentials", "", "", "");

        s.apply(new HttpHeaders());

        verify(oAuthClient).generateTokenWithClientCredentials("SDPOnDemand.requests.ALL,SDPOnDemand.setup.READ");
    }

    @Test
    void apply_clientCredentials_cachedTokenReusedForMultipleCalls() {
        when(oAuthClient.generateTokenWithClientCredentials(any())).thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("client_credentials", "", "", "");

        s.apply(new HttpHeaders());
        s.apply(new HttpHeaders());
        s.apply(new HttpHeaders());

        verify(oAuthClient, times(1)).generateTokenWithClientCredentials(any());
    }

    @Test
    void apply_clientCredentials_fetchesNewTokenAfterExpiry() {
        when(oAuthClient.generateTokenWithClientCredentials(any()))
                .thenReturn(expiredToken("first-token"))
                .thenReturn(validToken("second-token"));
        OAuthAuthStrategy s = strategy("client_credentials", "", "", "");

        s.apply(new HttpHeaders()); // gets first-token, immediately expired
        s.apply(new HttpHeaders()); // fetches second-token

        verify(oAuthClient, times(2)).generateTokenWithClientCredentials(any());
    }

    // ---- authorization_code ----

    @Test
    void apply_authorizationCode_exchangesCodeOnFirstCall() {
        when(oAuthClient.generateTokenFromAuthCode(any(), any()))
                .thenReturn(token("access-abc", "refresh-xyz", 3600));
        OAuthAuthStrategy s = strategy("authorization_code", "one-time-code", "https://example.com/cb", "");

        s.apply(new HttpHeaders());

        verify(oAuthClient).generateTokenFromAuthCode("one-time-code", "https://example.com/cb");
        verify(oAuthClient, never()).refreshAccessToken(any());
    }

    @Test
    void apply_authorizationCode_usesRefreshTokenForSubsequentCalls() {
        when(oAuthClient.generateTokenFromAuthCode(any(), any()))
                .thenReturn(token("access-1", "refresh-xyz", 1)); // expires immediately
        when(oAuthClient.refreshAccessToken("refresh-xyz"))
                .thenReturn(validToken("access-2"));
        OAuthAuthStrategy s = strategy("authorization_code", "one-time-code", "https://example.com/cb", "");

        s.apply(new HttpHeaders()); // exchange code → access-1 (expired) + refresh-xyz cached
        s.apply(new HttpHeaders()); // uses refresh-xyz → access-2

        verify(oAuthClient, times(1)).generateTokenFromAuthCode(any(), any());
        verify(oAuthClient, times(1)).refreshAccessToken("refresh-xyz");
    }

    @Test
    void apply_authorizationCode_usesConfiguredRefreshTokenDirectly() {
        when(oAuthClient.refreshAccessToken("pre-configured-refresh"))
                .thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("authorization_code", "", "", "pre-configured-refresh");

        s.apply(new HttpHeaders());

        verify(oAuthClient).refreshAccessToken("pre-configured-refresh");
        verify(oAuthClient, never()).generateTokenFromAuthCode(any(), any());
    }

    @Test
    void apply_authorizationCode_setsCorrectTokenInHeader() {
        when(oAuthClient.generateTokenFromAuthCode(any(), any()))
                .thenReturn(token("access-abc", null, 3600));
        OAuthAuthStrategy s = strategy("authorization_code", "code-123", "https://example.com/cb", "");
        HttpHeaders headers = new HttpHeaders();

        s.apply(headers);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-abc");
    }

    // ---- refresh_token ----

    @Test
    void apply_refreshToken_usesConfiguredRefreshToken() {
        when(oAuthClient.refreshAccessToken("configured-refresh"))
                .thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("refresh_token", "", "", "configured-refresh");

        s.apply(new HttpHeaders());

        verify(oAuthClient).refreshAccessToken("configured-refresh");
        verify(oAuthClient, never()).generateTokenWithClientCredentials(any());
        verify(oAuthClient, never()).generateTokenFromAuthCode(any(), any());
    }

    @Test
    void apply_refreshToken_noRefreshTokenConfigured_throwsIllegalStateException() {
        OAuthAuthStrategy s = strategy("refresh_token", "", "", "");

        assertThatThrownBy(() -> s.apply(new HttpHeaders()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh token");
    }

    @Test
    void apply_refreshToken_cachedTokenReusedForMultipleCalls() {
        when(oAuthClient.refreshAccessToken(any())).thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("refresh_token", "", "", "configured-refresh");

        s.apply(new HttpHeaders());
        s.apply(new HttpHeaders());

        verify(oAuthClient, times(1)).refreshAccessToken(any());
    }

    // ---- revokeToken ----

    @Test
    void revokeToken_delegatesToOAuthClient() {
        when(oAuthClient.generateTokenWithClientCredentials(any())).thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("client_credentials", "", "", "");
        s.apply(new HttpHeaders()); // populate cache

        s.revokeToken("access-abc");

        verify(oAuthClient).revokeToken("access-abc");
    }

    @Test
    void revokeToken_clearsCachedToken_whenRevokingCurrentAccessToken() {
        when(oAuthClient.generateTokenWithClientCredentials(any()))
                .thenReturn(validToken("access-abc"))
                .thenReturn(validToken("access-def"));
        OAuthAuthStrategy s = strategy("client_credentials", "", "", "");
        s.apply(new HttpHeaders()); // cache access-abc

        s.revokeToken("access-abc"); // should clear the cache

        HttpHeaders headers = new HttpHeaders();
        s.apply(headers); // should fetch a fresh token
        verify(oAuthClient, times(2)).generateTokenWithClientCredentials(any());
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-def");
    }

    @Test
    void revokeToken_doesNotClearCache_whenRevokingDifferentToken() {
        when(oAuthClient.generateTokenWithClientCredentials(any())).thenReturn(validToken("access-abc"));
        OAuthAuthStrategy s = strategy("client_credentials", "", "", "");
        s.apply(new HttpHeaders()); // cache access-abc

        s.revokeToken("some-other-token"); // different token — cache should stay

        s.apply(new HttpHeaders()); // should still use cached access-abc
        verify(oAuthClient, times(1)).generateTokenWithClientCredentials(any()); // no second fetch
    }
}
