package com.djmahirnationtv.status.backend.integration;

import com.djmahirnationtv.status.backend.monitor.MonitorService;
import com.djmahirnationtv.status.backend.monitor.dto.MonitorRequest;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:integration-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "app.integrations.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationTests {
    private static final String URL = "https://discord.com/api/webhooks/123456789012345678/test_token_not_a_real_webhook";
    private static final String REQUEST = "{\"name\":\"Operations\",\"webhookUrl\":\"" + URL + "\"}";
    @Autowired MockMvc mvc;
    @Autowired IntegrationService service;
    @Autowired DiscordIntegrationRepository integrations;
    @Autowired MonitorService monitorService;
    @Autowired MonitorRepository monitors;
    @Autowired WebhookSecrets secrets;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;
    @MockitoBean DiscordWebhookClient discord;

    @BeforeEach
    void prepare() {
        monitors.deleteAll();
        integrations.deleteAll();
        when(discord.send(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void savesEncryptedSecretsAndListsOnlyOwnedNames() throws Exception {
        mvc.perform(post("/api/integrations").with(user("1")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Operations"))
                .andExpect(jsonPath("$.webhookUrl").doesNotExist()).andExpect(jsonPath("$.encryptedWebhook").doesNotExist());
        var stored = integrations.findAll().getFirst();
        assertThat(stored.getEncryptedWebhook()).doesNotContain("discord.com").doesNotContain("test_token");
        assertThat(secrets.decrypt(stored.getEncryptedWebhook())).isEqualTo(URL);
        mvc.perform(get("/api/integrations").with(user("1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.integrations.length()").value(1)).andExpect(jsonPath("$.integrations[0].webhookUrl").doesNotExist());
        mvc.perform(get("/api/integrations").with(user("2"))).andExpect(jsonPath("$.integrations.length()").value(0));
    }

    @Test
    void allActionsRequireAuthenticationOwnershipAndCsrf() throws Exception {
        Long id = service.save(null, "Operations", URL, 1L).id();
        String path = "/api/integrations/" + id;
        mvc.perform(get("/api/integrations")).andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/test").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/test").with(user("1"))).andExpect(status().isForbidden());
        mvc.perform(post(path + "/test").with(user("2")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(path).with(user("2")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put(path).with(user("2")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isNotFound());
        mvc.perform(post("/api/integrations").with(user("1")).contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isForbidden());
        verifyNoInteractions(discord);
    }

    @Test
    void rejectsNonDiscordUrlsAndInvalidInput() throws Exception {
        for (String url : List.of("http://127.0.0.1/api/webhooks/1/secret", URL + "?redirect=bad", URL.replace("discord.com", "discord.com.example.com"), URL.replace("https://", "http://"), URL.replace("discord.com", "discord.com@127.0.0.1"))) {
            mvc.perform(post("/api/integrations").with(user("1")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(REQUEST.replace(URL, url))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/integrations").with(user("1")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST.replace("Operations", " "))).andExpect(status().isBadRequest());
        assertThat(integrations.count()).isZero();
        verifyNoInteractions(discord);
    }

    @Test
    void blankWebhookOnEditKeepsSecretAndTestUsesSavedUrl() throws Exception {
        Long id = service.save(null, "Operations", URL, 1L).id();
        mvc.perform(put("/api/integrations/" + id).with(user("1")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"webhookUrl\":\"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"));
        mvc.perform(post("/api/integrations/" + id + "/test").with(user("1")).with(csrf())).andExpect(status().isNoContent());
        verify(discord).send(eq(URL), contains("connected"));
        when(discord.send(anyString(), anyString())).thenReturn(false);
        mvc.perform(post("/api/integrations/" + id + "/test").with(user("1")).with(csrf())).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Discord did not accept the message. Check your webhook or try again later."));
    }

    @Test
    void monitorSelectionIsPrivateAndCannotUseAnotherUsersWebhook() throws Exception {
        Long integrationId = service.save(null, "Operations", URL, 1L).id();
        var monitor = monitorService.createMonitor(settings(List.of(integrationId)), 1L);
        mvc.perform(get("/api/integrations/monitors/" + monitor.id()).with(user("1"))).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(integrationId));
        mvc.perform(get("/api/integrations/monitors/" + monitor.id()).with(user("2"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/integrations/monitors/" + monitor.id())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/monitors/" + monitor.id())).andExpect(status().isOk()).andExpect(jsonPath("$.integrations").doesNotExist()).andExpect(jsonPath("$.integrationIds").doesNotExist());
        assertThatThrownBy(() -> monitorService.createMonitor(settings(List.of(integrationId)), 2L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(monitors.count()).isEqualTo(1);
        monitorService.updateMonitor(monitor.id(), settings(List.of()), 1L);
        assertThat(monitorService.getIntegrationIds(monitor.id(), 1L)).isEmpty();
    }

    @Test
    void deletingIntegrationDetachesItWithoutDeletingMonitors() {
        Long integrationId = service.save(null, "Operations", URL, 1L).id();
        var monitor = monitorService.createMonitor(settings(List.of(integrationId)), 1L);
        service.delete(integrationId, 1L);
        assertThat(integrations.existsById(integrationId)).isFalse();
        assertThat(monitors.existsById(monitor.id())).isTrue();
        assertThat(monitorService.getIntegrationIds(monitor.id(), 1L)).isEmpty();
    }

    @Test
    void alertsRunOnlyAfterCommitAndFailuresDoNotUndoMonitorChanges() {
        Long integrationId = service.save(null, "Operations", URL, 1L).id();
        var monitor = monitorService.createMonitor(settings(List.of(integrationId)), 1L);
        var event = new MonitorStatusChanged(monitor.id(), 1L, "Website", true);
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            events.publishEvent(event);
            verifyNoInteractions(discord);
            status.setRollbackOnly();
        });
        verifyNoInteractions(discord);
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        verify(discord).send(URL, "StatusRobot: Website is down.");
        when(discord.send(anyString(), anyString())).thenThrow(new IllegalStateException("unavailable"));
        transaction.executeWithoutResult(status -> {
            monitors.findById(monitor.id()).orElseThrow().setName("Saved name");
            events.publishEvent(new MonitorStatusChanged(monitor.id(), 1L, "Website", false));
        });
        assertThat(monitors.findById(monitor.id()).orElseThrow().getName()).isEqualTo("Saved name");
        verify(discord).send(URL, "StatusRobot: Website is back online.");
    }

    private MonitorRequest settings(List<Long> ids) {
        return new MonitorRequest("Website", "https://example.com", "GET", 60, 5, ids);
    }
}
