package com.djmahirnationtv.status.backend.auth;

import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth-tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {
    private static final String PASSWORD = "four quiet green trees";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MonitorRepository monitors;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;
    private final JsonMapper json = new JsonMapper();
    private String email;

    @BeforeEach
    void prepare() {
        monitors.deleteAll();
        users.deleteAll();
        email = UUID.randomUUID() + "@example.test";
    }

    @Test
    void registrationHashesPasswordAndValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(passwords.matches(PASSWORD, users.findByEmail(email).orElseThrow().getPasswordHash())).isTrue();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isConflict());
        for (String invalid : new String[]{"short", "é".repeat(40)}) {
            mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", "new@example.test", "password", invalid))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "invalid", "password", PASSWORD))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void realCsrfTokensSessionRotationAndLogoutWorkTogether() throws Exception {
        auth.register(email, PASSWORD);
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String originalId = session.getId();
        JsonNode token = json.readTree(csrfResult.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/login").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .param("email", email.toUpperCase()).param("password", PASSWORD))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(originalId);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isForbidden());
        JsonNode freshToken = json.readTree(mvc.perform(get("/api/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").session(session)
                        .header(freshToken.get("headerName").asText(), freshToken.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void failedLoginDoesNotCreateAuthenticatedSession() throws Exception {
        auth.register(email, PASSWORD);
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", email).param("password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid email or password."));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfIsRequiredForEveryWriteAndPublicReadsStillWork() throws Exception {
        for (String path : new String[]{"/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/monitors"}) {
            mvc.perform(post(path)).andExpect(status().isForbidden());
            mvc.perform(post(path).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/monitors")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/monitors").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/monitors/1/toggle-pause").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/monitors/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheCreatorCanChangeAMonitor() throws Exception {
        AppUser owner = auth.register(email, PASSWORD);
        AppUser other = auth.register("other@example.test", PASSWORD);
        var result = mvc.perform(post("/api/monitors").with(csrf()).with(user(owner.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Website", "url":"https://example.com", "httpMethod":"GET",
                         "intervalSeconds":60, "timeoutSeconds":10, "ownerId":999999}
                        """))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(monitors.findById(id).orElseThrow().getOwnerId()).isEqualTo(owner.getId());
        mvc.perform(patch("/api/monitors/" + id + "/toggle-pause").with(csrf()).with(user(other.getId().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/monitors/" + id).with(csrf()).with(user(other.getId().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/monitors/" + id + "/toggle-pause").with(csrf()).with(user(owner.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(delete("/api/monitors/" + id).with(csrf()).with(user(owner.getId().toString())))
                .andExpect(status().isNoContent());
    }

    @Test
    void oauthUsesProviderIdentityAndNeverAutomaticallyLinksEmail() {
        AppUser github = auth.oauthUser("github", "123", email);
        assertThat(github.getPasswordHash()).isNull();
        assertThat(auth.oauthUser("github", "123", "changed@example.test").getId()).isEqualTo(github.getId());
        assertThatThrownBy(() -> auth.loadUserByUsername(email))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
        assertThatThrownBy(() -> auth.oauthUser("discord", "456", email))
                .isInstanceOf(OAuth2AuthenticationException.class);
        auth.register("local@example.test", PASSWORD);
        assertThatThrownBy(() -> auth.oauthUser("github", "789", "local@example.test"))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void unrequestedOAuthCallbackCannotLogAnyoneIn() throws Exception {
        mvc.perform(get("/login/oauth2/code/github").param("code", "fake").param("state", "fake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/#login?error=oauth"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
