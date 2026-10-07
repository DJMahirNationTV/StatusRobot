package com.djmahirnationtv.status.backend.statuspage;

import com.djmahirnationtv.status.backend.auth.AppUser;
import com.djmahirnationtv.status.backend.statuspage.StatusPageService.StatusPageRequest;
import com.djmahirnationtv.status.backend.auth.UserRepository;
import com.djmahirnationtv.status.backend.monitor.MonitorService;
import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import com.djmahirnationtv.status.backend.incident.IncidentService;
import com.djmahirnationtv.status.backend.incident.IncidentRepository;
import com.djmahirnationtv.status.backend.incident.Incident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:status-page-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.djmahirnationtv.status.backend.statuspage.StatusPageTests$LockSqlInspector"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusPageTests {
    @Autowired MockMvc mvc;
    @Autowired StatusPageService service;
    @Autowired StatusPageRepository pages;
    @Autowired MonitorRepository monitors;
    @Autowired MonitorService monitorService;
    @Autowired UserRepository users;
    @Autowired IncidentService incidents;
    @Autowired IncidentRepository incidentRepository;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;
    private Long ownerId;
    private Long otherId;
    private Monitor monitor;

    @BeforeEach
    void prepare() {
        LockSqlInspector.queries.clear();
        pages.deleteAll(); incidentRepository.deleteAll(); monitors.deleteAll(); users.deleteAll();
        ownerId = users.saveAndFlush(new AppUser("owner@example.test", null, "local", null)).getId();
        otherId = users.saveAndFlush(new AppUser("other@example.test", null, "local", null)).getId();
        monitor = new Monitor("Website", "https://example.com", "GET", 60, 5);
        monitor.setOwnerId(ownerId);
        monitor = monitors.saveAndFlush(monitor);
    }

    @Test
    void publicIncidentsRequirePublicationAndMatchThePageOwnerAndSelectedMonitors() throws Exception {
        var page = service.save(null, settings("public-incidents", true), ownerId);
        var incident = incidents.create(new IncidentService.CreateRequest(monitor.getId(), "Delayed requests", "We are investigating."), ownerId);
        mvc.perform(get("/api/status-pages/public-incidents")).andExpect(jsonPath("$.incidents").isEmpty());
        incidents.publish(incident.id(), new IncidentService.PublicationRequest("Delayed requests", true), ownerId);
        incidents.update(incident.id(), new IncidentService.UpdateRequest(Incident.Stage.MONITORING, "A fix is being checked."), ownerId);
        var privateIncident = incidents.create(new IncidentService.CreateRequest(monitor.getId(), "Private incident", "Internal note"), ownerId);
        var foreign = Incident.manual(otherId, monitor.getId(), "Website", "Other user's incident", "Other user's message");
        foreign.publish("Other user's incident", true);
        incidentRepository.saveAndFlush(foreign);
        mvc.perform(get("/api/status-pages/public-incidents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents.length()").value(1))
                .andExpect(jsonPath("$.incidents[0].title").value("Delayed requests"))
                .andExpect(jsonPath("$.incidents[0].updates.length()").value(2))
                .andExpect(jsonPath("$.incidents[0].cause").doesNotExist())
                .andExpect(jsonPath("$.incidents[0].ownerId").doesNotExist());
        mvc.perform(get("/api/status-pages/default")).andExpect(jsonPath("$.incidents[0].id").value(incident.id()));
        assertThat(incidents.get(privateIncident.id(), ownerId).published()).isFalse();
        service.save(null, new StatusPageRequest("Empty", "empty-selection", "", List.of(), false), ownerId);
        mvc.perform(get("/api/status-pages/empty-selection")).andExpect(jsonPath("$.incidents").isEmpty());
        incidents.publish(incident.id(), new IncidentService.PublicationRequest("Delayed requests", false), ownerId);
        mvc.perform(get("/api/status-pages/public-incidents")).andExpect(jsonPath("$.incidents").isEmpty());
        incidents.publish(incident.id(), new IncidentService.PublicationRequest("Delayed requests", true), ownerId);
        service.save(page.id(), new StatusPageRequest("Status", "public-incidents", "", List.of(), true), ownerId);
        mvc.perform(get("/api/status-pages/public-incidents")).andExpect(jsonPath("$.incidents").isEmpty());
    }

    @Test
    void createsMultiplePagesAndPublicViewsOnlyShowSelectedMonitors() throws Exception {
        mvc.perform(get("/api/status-pages/default")).andExpect(status().isNoContent());
        mvc.perform(post("/api/status-pages").with(user(ownerId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(input("Website status", "website-status", false, monitor.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.monitors.length()").value(1));
        service.save(null, new StatusPageRequest("API status", "api-status", "", List.of(), false), ownerId);
        mvc.perform(get("/api/status-pages/mine").with(user(ownerId.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canPinDefault").value(true)).andExpect(jsonPath("$.pages.length()").value(2));
        mvc.perform(get("/api/status-pages/website-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.monitors[0].name").value("Website"))
                .andExpect(jsonPath("$.ownerId").doesNotExist()).andExpect(jsonPath("$.monitors[0].integrations").doesNotExist());
        mvc.perform(get("/api/status-pages/api-status")).andExpect(jsonPath("$.monitors.length()").value(0));
        mvc.perform(get("/api/status-pages/unknown")).andExpect(status().isNotFound());
    }

    @Test
    void pinningReplacesOnlyTheDefaultAndUnpinningRestoresTheLandingPage() throws Exception {
        var first = service.save(null, settings("first-page", true), ownerId);
        var second = service.save(null, settings("second-page", false), ownerId);
        mvc.perform(get("/api/status-pages/default")).andExpect(jsonPath("$.id").value(first.id()));
        mvc.perform(patch("/api/status-pages/" + second.id() + "/pin-default").with(user(ownerId.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"pinnedDefault\":true}")).andExpect(status().isOk());
        assertThat(pages.findAll().stream().filter(StatusPage::isPinnedDefault)).hasSize(1);
        assertThat(service.defaultPage().id()).isEqualTo(second.id());
        assertThat(service.bySlug("first-page").pinnedDefault()).isFalse();
        service.pin(second.id(), false, ownerId);
        mvc.perform(get("/api/status-pages/default")).andExpect(status().isNoContent());
        assertThat(pages.count()).isEqualTo(2);
    }

    @Test
    void changesRequireAuthenticationOwnershipCsrfAndDefaultPermission() throws Exception {
        var page = service.save(null, settings("owner-page", false), ownerId);
        String path = "/api/status-pages/" + page.id();
        mvc.perform(get("/api/status-pages/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/status-pages/mine").with(user(otherId.toString()))).andExpect(jsonPath("$.pages.length()").value(0)).andExpect(jsonPath("$.canPinDefault").value(false));
        mvc.perform(delete(path).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(path).with(user(ownerId.toString()))).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(user(otherId.toString())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put(path).with(user(otherId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(input("Changed", "changed-page", false, monitor.getId()))).andExpect(status().isNotFound());
        mvc.perform(patch(path + "/pin-default").with(user(otherId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinnedDefault\":true}")).andExpect(status().isNotFound());
        var otherPage = service.save(null, new StatusPageRequest("Other", "other-page", "", List.of(), false), otherId);
        mvc.perform(patch("/api/status-pages/" + otherPage.id() + "/pin-default").with(user(otherId.toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"pinnedDefault\":true}")).andExpect(status().isForbidden());
        assertThat(service.defaultPage()).isNull();
    }

    @Test
    void rejectsDuplicateAddressesInvalidInputAndOtherUsersMonitors() throws Exception {
        service.save(null, settings("existing-page", false), ownerId);
        mvc.perform(post("/api/status-pages").with(user(ownerId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(input("Duplicate", "existing-page", false, monitor.getId()))).andExpect(status().isConflict());
        for (String slug : List.of("UpperCase", "bad/slash", "a", "--bad")) {
            mvc.perform(post("/api/status-pages").with(user(ownerId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(input("Invalid", slug, false, monitor.getId()))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/status-pages").with(user(ownerId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(input("Reserved", "default", false, monitor.getId()))).andExpect(status().isConflict());
        mvc.perform(post("/api/status-pages").with(user(otherId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(input("Other", "other-page", false, monitor.getId()))).andExpect(status().isBadRequest());
        assertThat(pages.count()).isEqualTo(1);
    }

    @Test
    void editsSelectionAndDeletesPagesWithoutDeletingMonitorsOrHistory() throws Exception {
        var page = service.save(null, settings("my-page", true), ownerId);
        mvc.perform(put("/api/status-pages/" + page.id()).with(user(ownerId.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"slug\":\"renamed-page\",\"description\":\"Updated\",\"monitorIds\":[],\"pinnedDefault\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.monitors.length()").value(0));
        mvc.perform(delete("/api/status-pages/" + page.id()).with(user(ownerId.toString())).with(csrf())).andExpect(status().isNoContent());
        assertThat(monitors.existsById(monitor.getId())).isTrue();
        assertThat(service.defaultPage()).isNull();
        verifyNoInteractions(pings);
    }

    @Test
    void deletingAMonitorRemovesItFromStatusPages() {
        service.save(null, settings("my-page", false), ownerId);
        monitorService.deleteMonitor(monitor.getId(), ownerId);
        assertThat(service.bySlug("my-page").monitors()).isEmpty();
        assertThat(pages.count()).isEqualTo(1);
    }

    @Test
    void historyIsPublicAndRejectsInvalidRangesAndMissingMonitors() throws Exception {
        mvc.perform(get("/api/monitors/" + monitor.getId() + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.length()").value(90))
                .andExpect(jsonPath("$.periods[0].checks").value(0))
                .andExpect(jsonPath("$.periods[0].uptimePercentage").isEmpty());
        mvc.perform(get("/api/monitors/" + monitor.getId() + "/history?hours=2")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/monitors/999999/history")).andExpect(status().isNotFound());
    }

    @Test
    void ownerLockUsesPlainForUpdateWithoutAnAlias() {
        service.save(null, settings("plain-lock", false), ownerId);
        assertThat(LockSqlInspector.queries).singleElement().satisfies(sql ->
                assertThat(sql.toLowerCase(Locale.ROOT))
                        .contains("from app_users where id = ? for update")
                        .doesNotContain("for update of"));
    }

    @Test
    void concurrentPinRequestsLeaveOnlyOneDefault() throws Exception {
        var first = service.save(null, settings("first-page", false), ownerId);
        var second = service.save(null, settings("second-page", false), ownerId);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return service.pin(first.id(), true, ownerId); });
            var b = executor.submit(() -> { start.await(); return service.pin(second.id(), true, ownerId); });
            start.countDown();
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertThat(pages.findAll().stream().filter(StatusPage::isPinnedDefault)).hasSize(1);
        assertThat(service.defaultPage().id()).isIn(first.id(), second.id());
    }

    private StatusPageRequest settings(String slug, boolean pinned) {
        return new StatusPageRequest("Status", slug, "Service availability", List.of(monitor.getId()), pinned);
    }
    private String input(String name, String slug, boolean pinned, Long monitorId) {
        return "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"description\":\"\",\"monitorIds\":[" + monitorId + "],\"pinnedDefault\":" + pinned + "}";
    }

    public static class LockSqlInspector implements org.hibernate.resource.jdbc.spi.StatementInspector {
        static final ConcurrentLinkedQueue<String> queries = new ConcurrentLinkedQueue<>();

        @Override
        public String inspect(String sql) {
            if (sql.toLowerCase(Locale.ROOT).contains("for update")) queries.add(sql);
            return sql;
        }
    }
}
