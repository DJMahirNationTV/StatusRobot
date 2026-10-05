package com.djmahirnationtv.status.backend.auth;

import com.djmahirnationtv.status.backend.config.OAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OAuthUserServiceTests {
    private final AuthService auth = mock(AuthService.class);
    private final ClientRegistrationRepository providers = new OAuthConfig().clientRegistrations(new MockEnvironment()
            .withProperty("app.oauth.redirect-base-url", "https://status.djmahirnationtv.com")
            .withProperty("app.oauth.github.client-id", "github-test-id")
            .withProperty("app.oauth.github.client-secret", "test-secret")
            .withProperty("app.oauth.discord.client-id", "discord-test-id")
            .withProperty("app.oauth.discord.client-secret", "test-secret"));
    private MockRestServiceServer server;
    private OAuthUserService service;

    @BeforeEach
    void prepare() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OAuthUserService(auth, builder.build());
    }

    @Test
    void githubUsesVerifiedPrimaryEmailEvenWhenProfileEmailIsPrivate() {
        server.expect(requestTo("https://api.github.com/user"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"id\":42,\"email\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        [{"email":"secondary@example.test","primary":false,"verified":true},
                         {"email":"user@example.test","primary":true,"verified":true,"visibility":null}]
                        """, MediaType.APPLICATION_JSON));
        givenUser("github", "42");
        assertThat(service.loadUser(request("github")).getName()).isEqualTo("7");
        verify(auth).oauthUser("github", "42", "user@example.test");
        server.verify();
    }

    @Test
    void discordUsesVerifiedEmailAndStableUserId() {
        server.expect(requestTo("https://discord.com/api/users/@me"))
                .andRespond(withSuccess("{\"id\":\"42\",\"email\":\"user@example.test\",\"verified\":true}", MediaType.APPLICATION_JSON));
        givenUser("discord", "42");
        assertThat(service.loadUser(request("discord")).getName()).isEqualTo("7");
        verify(auth).oauthUser("discord", "42", "user@example.test");
        server.verify();
    }

    @Test
    void rejectsUnverifiedDiscordEmail() {
        server.expect(requestTo("https://discord.com/api/users/@me"))
                .andRespond(withSuccess("{\"id\":\"42\",\"email\":\"user@example.test\",\"verified\":false}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.loadUser(request("discord"))).isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void rejectsUnverifiedGithubEmail() {
        server.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/user/emails"))
                .andRespond(withSuccess("[{\"email\":\"user@example.test\",\"primary\":true,\"verified\":false}]", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.loadUser(request("github"))).isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void providerFailuresDoNotCreateAccounts() {
        server.expect(requestTo("https://api.github.com/user")).andRespond(withServerError());
        assertThatThrownBy(() -> service.loadUser(request("github"))).isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(auth);
    }

    @Test
    void providersRequireCredentialsAndUseExplicitCallbacks() {
        var empty = new OAuthConfig().clientRegistrations(new MockEnvironment()
                .withProperty("app.oauth.redirect-base-url", "http://localhost:5173"));
        assertThat(empty.findByRegistrationId("github")).isNull();
        assertThat(empty.findByRegistrationId("discord")).isNull();
        for (String name : new String[]{"github", "discord"}) {
            assertThat(providers.findByRegistrationId(name).getRedirectUri())
                    .isEqualTo("https://status.example.test/login/oauth2/code/" + name);
        }
    }

    private void givenUser(String provider, String providerId) {
        var user = mock(AppUser.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("user@example.test");
        when(auth.oauthUser(provider, providerId, "user@example.test")).thenReturn(user);
    }

    private OAuth2UserRequest request(String provider) {
        return new OAuth2UserRequest(providers.findByRegistrationId(provider),
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-token", Instant.now(), Instant.now().plusSeconds(60)));
    }
}
