package com.fourimpact.sdpsinkconnector.client.auth;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZohoOAuthClientTest {

    private WireMockServer wireMock;
    private ZohoOAuthClient client;

    private static final String TOKEN_PATH        = "/oauth/v2/token";
    private static final String REVOKE_PATH       = "/oauth/v2/token/revoke";
    private static final String CLIENT_ID         = "test-client-id";
    private static final String CLIENT_SECRET     = "test-client-secret";

    private static final String FULL_TOKEN_BODY = """
            {
              "access_token":  "access-abc",
              "refresh_token": "refresh-xyz",
              "expires_in":    3600,
              "token_type":    "Bearer",
              "api_domain":    "https://api.zoho.com"
            }
            """;

    private static final String NO_REFRESH_TOKEN_BODY = """
            {
              "access_token": "access-abc",
              "expires_in":   3600,
              "token_type":   "Bearer",
              "api_domain":   "https://api.zoho.com"
            }
            """;

    private static final String ERROR_BODY = """
            {"error": "invalid_grant"}
            """;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        String base = "http://localhost:" + wireMock.port();
        client = new ZohoOAuthClient(
                base + TOKEN_PATH,
                base + REVOKE_PATH,
                CLIENT_ID,
                CLIENT_SECRET
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // ---- generateTokenFromAuthCode ----

    @Test
    void generateTokenFromAuthCode_success_returnsAccessAndRefreshTokens() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(FULL_TOKEN_BODY)));

        OAuthTokenResponse response = client.generateTokenFromAuthCode("auth-code-123", "https://example.com/cb");

        assertThat(response.getAccessToken()).isEqualTo("access-abc");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-xyz");
        assertThat(response.getExpiresIn()).isEqualTo(3600);
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getApiDomain()).isEqualTo("https://api.zoho.com");
    }

    @Test
    void generateTokenFromAuthCode_sendsCorrectFormParams() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(FULL_TOKEN_BODY)));

        client.generateTokenFromAuthCode("auth-code-123", "https://example.com/cb");

        wireMock.verify(postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withFormParam("grant_type",    equalTo("authorization_code"))
                .withFormParam("code",           equalTo("auth-code-123"))
                .withFormParam("redirect_uri",   equalTo("https://example.com/cb"))
                .withFormParam("client_id",      equalTo(CLIENT_ID))
                .withFormParam("client_secret",  equalTo(CLIENT_SECRET)));
    }

    @Test
    void generateTokenFromAuthCode_errorResponse_throwsIllegalStateException() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(ERROR_BODY)));

        assertThatThrownBy(() -> client.generateTokenFromAuthCode("bad-code", "https://example.com/cb"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
    }

    // ---- refreshAccessToken ----

    @Test
    void refreshAccessToken_success_returnsNewAccessToken() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(NO_REFRESH_TOKEN_BODY)));

        OAuthTokenResponse response = client.refreshAccessToken("refresh-xyz");

        assertThat(response.getAccessToken()).isEqualTo("access-abc");
        assertThat(response.getRefreshToken()).isNull();
        assertThat(response.getExpiresIn()).isEqualTo(3600);
    }

    @Test
    void refreshAccessToken_sendsCorrectFormParams() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(NO_REFRESH_TOKEN_BODY)));

        client.refreshAccessToken("refresh-xyz");

        wireMock.verify(postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withFormParam("grant_type",    equalTo("refresh_token"))
                .withFormParam("refresh_token", equalTo("refresh-xyz"))
                .withFormParam("client_id",     equalTo(CLIENT_ID))
                .withFormParam("client_secret", equalTo(CLIENT_SECRET)));
    }

    @Test
    void refreshAccessToken_errorResponse_throwsIllegalStateException() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(ERROR_BODY)));

        assertThatThrownBy(() -> client.refreshAccessToken("expired-refresh-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
    }

    // ---- generateTokenWithClientCredentials ----

    @Test
    void generateTokenWithClientCredentials_success_returnsAccessToken() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(NO_REFRESH_TOKEN_BODY)));

        OAuthTokenResponse response = client.generateTokenWithClientCredentials("SDPOnDemand.requests.ALL");

        assertThat(response.getAccessToken()).isEqualTo("access-abc");
        assertThat(response.getRefreshToken()).isNull();
    }

    @Test
    void generateTokenWithClientCredentials_sendsCorrectFormParams() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(NO_REFRESH_TOKEN_BODY)));

        client.generateTokenWithClientCredentials("SDPOnDemand.requests.ALL,SDPOnDemand.setup.READ");

        wireMock.verify(postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withFormParam("grant_type",    equalTo("client_credentials"))
                .withFormParam("scope",         equalTo("SDPOnDemand.requests.ALL,SDPOnDemand.setup.READ"))
                .withFormParam("client_id",     equalTo(CLIENT_ID))
                .withFormParam("client_secret", equalTo(CLIENT_SECRET)));
    }

    @Test
    void generateTokenWithClientCredentials_errorResponse_throwsIllegalStateException() {
        wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson(ERROR_BODY)));

        assertThatThrownBy(() -> client.generateTokenWithClientCredentials("SDPOnDemand.requests.ALL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
    }

    // ---- revokeToken ----

    @Test
    void revokeToken_success_postsTokenToRevokeEndpoint() {
        wireMock.stubFor(post(urlPathEqualTo(REVOKE_PATH))
                .willReturn(okJson("{\"status\":\"success\"}")));

        client.revokeToken("access-abc");

        wireMock.verify(postRequestedFor(urlPathEqualTo(REVOKE_PATH))
                .withFormParam("token", equalTo("access-abc")));
    }

    @Test
    void revokeToken_doesNotPostToTokenEndpoint() {
        wireMock.stubFor(post(urlPathEqualTo(REVOKE_PATH))
                .willReturn(okJson("{\"status\":\"success\"}")));

        client.revokeToken("access-abc");

        wireMock.verify(0, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    }
}
