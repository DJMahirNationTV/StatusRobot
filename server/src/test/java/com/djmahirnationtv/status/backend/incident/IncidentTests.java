package com.djmahirnationtv.status.backend.incident;

import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.monitor.MonitorService;
import com.djmahirnationtv.status.backend.ping.PingExecutionService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:incident-tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IncidentTests {
    @Autowired IncidentRepository incidents;
    @Autowired IncidentService service;
    @Autowired MockMvc mvc;
    @Autowired MonitorRepository monitors;
    @Autowired MonitorService monitorService;
    @Autowired PingExecutionService execution;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;

    @BeforeEach
    void prepare() {
        incidents.deleteAll();
        monitors.deleteAll();
    }

    @Test
    void storesOutageAndRecoveryTimes() {
        Instant started = Instant.parse("2026-10-07T08:00:00Z");
        var incident = incidents.saveAndFlush(new Incident(1L, 10L, "Website", "HTTP 503", started));
        assertThat(incidents.findByOpenMonitorId(10L)).isPresent();
        incident.resolve(started.plusSeconds(120));
        incidents.saveAndFlush(incident);

        var saved = incidents.findById(incident.getId()).orElseThrow();
        assertThat(saved.getStartedAt()).isEqualTo(started);
        assertThat(saved.getResolvedAt()).isEqualTo(started.plusSeconds(120));
        assertThat(incidents.findByOpenMonitorId(10L)).isEmpty();
        incidents.saveAndFlush(new Incident(1L, 10L, "Website", "HTTP 502", started.plusSeconds(300)));
        assertThat(incidents.count()).isEqualTo(2);
    }

    @Test
    void databasePreventsTwoOpenIncidentsForOneMonitor() {
        incidents.saveAndFlush(new Incident(1L, 10L, "Website", "HTTP 503", Instant.now()));
        assertThatThrownBy(() -> incidents.saveAndFlush(
                new Incident(1L, 10L, "Website", "HTTP 502", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(incidents.count()).isEqualTo(1);
    }

    @Test
    void listingIsPrivateFilteredAndPaged() {
        Instant started = Instant.parse("2026-10-07T08:00:00Z");
        for (long id = 1; id <= 21; id++) {
            incidents.save(new Incident(1L, id, "Website " + id, "HTTP 503", started.plusSeconds(id)));
        }
        var resolved = new Incident(1L, 22L, "API", "HTTP 502", started);
        resolved.resolve(started.plusSeconds(60));
        incidents.save(resolved);
        incidents.save(new Incident(2L, 23L, "Private monitor", "HTTP 500", started));

        var first = service.mine(1L, "all", 0);
        assertThat(first.totalElements()).isEqualTo(22);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.incidents()).hasSize(20);
        assertThat(first.incidents().getFirst().monitorId()).isEqualTo(21L);
        assertThat(service.mine(1L, "all", 1).incidents()).hasSize(2);
        assertThat(service.mine(1L, "open", 0).totalElements()).isEqualTo(21);
        assertThat(service.mine(1L, "resolved", 0).incidents()).hasSize(1);
        assertThat(service.mine(3L, "all", 0).incidents()).isEmpty();
        assertThatThrownBy(() -> service.mine(1L, "unknown", 0)).hasMessageContaining("400");
        assertThatThrownBy(() -> service.mine(1L, "all", -1)).hasMessageContaining("400");
    }

    @Test
    void apiRequiresLoginAndNeverShowsAnotherUsersIncident() throws Exception {
        var owned = incidents.saveAndFlush(new Incident(1L, 10L, "Website", "HTTP 503", Instant.now()));
        String path = "/api/incidents/" + owned.getId();
        mvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/incidents").with(user("2")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incidents").isEmpty());
        mvc.perform(get(path).with(user("2"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents/999999").with(user("1"))).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user("1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.monitorName").value("Website"))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.openMonitorId").doesNotExist());
        mvc.perform(get("/api/incidents").with(user("1")).param("status", "unknown"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/incidents").with(user("1")).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents").with(user("1")).param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedFailuresShareAnIncidentAndRecoveryClosesIt() {
        var monitor = checkedMonitor();
        service.recordCheck(monitor, 503);
        monitor.setLastCheckedAt(monitor.getLastCheckedAt().plusSeconds(60));
        service.recordCheck(monitor, 502);
        assertThat(incidents.count()).isEqualTo(1);
        var incident = incidents.findByOpenMonitorId(10L).orElseThrow();
        assertThat(incident.getCause()).isEqualTo("HTTP check returned 503.");

        monitor.setStatus(MonitorStatus.DEGRADED);
        monitor.setLastCheckedAt(monitor.getLastCheckedAt().plusSeconds(60));
        service.recordCheck(monitor, 200);
        service.recordCheck(monitor, 200);
        var resolved = incidents.findById(incident.getId()).orElseThrow();
        assertThat(resolved.getResolvedAt()).isEqualTo(monitor.getLastCheckedAt());
        assertThat(incidents.findByOpenMonitorId(10L)).isEmpty();

        monitor.setStatus(MonitorStatus.DOWN);
        monitor.setLastCheckedAt(monitor.getLastCheckedAt().plusSeconds(60));
        service.recordCheck(monitor, 0);
        assertThat(incidents.count()).isEqualTo(2);
        assertThat(incidents.findByOpenMonitorId(10L).orElseThrow().getCause())
                .isEqualTo("No HTTP response was received.");
    }

    @Test
    void pausingDoesNotClaimARecoveryAndLegacyMonitorsAreIgnored() {
        var monitor = checkedMonitor();
        service.recordCheck(monitor, 503);
        monitor.setStatus(MonitorStatus.PAUSED);
        service.recordCheck(monitor, 200);
        assertThat(incidents.findByOpenMonitorId(10L)).isPresent();
        monitor.setOwnerId(null);
        monitor.setId(11L);
        monitor.setStatus(MonitorStatus.DOWN);
        service.recordCheck(monitor, 503);
        assertThat(incidents.count()).isEqualTo(1);
    }

    @Test
    void realChecksPersistIncidentsAndRejectStaleRecovery() throws Exception {
        var httpStatus = new AtomicInteger(503);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(httpStatus.get(), -1);
            exchange.close();
        });
        server.start();
        try {
            var monitor = new Monitor("Website", "http://127.0.0.1:" + server.getAddress().getPort() + "/health", "GET", 60, 5);
            monitor.setOwnerId(1L);
            monitor.setStatus(MonitorStatus.UP);
            Long id = monitors.saveAndFlush(monitor).getId();
            var stale = monitors.findById(id).orElseThrow();

            execution.ping(monitors.findById(id).orElseThrow());
            execution.ping(monitors.findById(id).orElseThrow());
            assertThat(incidents.count()).isEqualTo(1);
            assertThat(incidents.findByOpenMonitorId(id)).isPresent();

            httpStatus.set(200);
            assertThatThrownBy(() -> execution.ping(stale))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(incidents.findByOpenMonitorId(id)).isPresent();
            assertThat(monitors.findById(id).orElseThrow().getStatus()).isEqualTo(MonitorStatus.DOWN);

            execution.ping(monitors.findById(id).orElseThrow());
            assertThat(incidents.findByOpenMonitorId(id)).isEmpty();
            assertThat(service.mine(1L, "resolved", 0).incidents()).hasSize(1);
            httpStatus.set(503);
            execution.ping(monitors.findById(id).orElseThrow());
            assertThat(incidents.count()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deletingAMonitorClearsOnlyItsIncidentHistory() throws Exception {
        var monitor = checkedMonitor();
        monitor.setId(null);
        monitor = monitors.saveAndFlush(monitor);
        service.recordCheck(monitor, 503);
        var other = incidents.saveAndFlush(new Incident(2L, 999L, "Other website", "HTTP 500", Instant.now()));
        String path = "/api/monitors/" + monitor.getId();
        mvc.perform(delete(path).with(user("2")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(path).with(user("1"))).andExpect(status().isForbidden());
        assertThat(incidents.count()).isEqualTo(2);

        monitorService.deleteMonitor(monitor.getId(), 1L);
        assertThat(incidents.count()).isEqualTo(1);
        assertThat(incidents.findById(other.getId())).isPresent();
        assertThat(monitors.findById(monitor.getId())).isEmpty();
    }

    private Monitor checkedMonitor() {
        var monitor = new Monitor("Website", "https://example.com", "GET", 60, 5);
        monitor.setId(10L);
        monitor.setOwnerId(1L);
        monitor.setStatus(MonitorStatus.DOWN);
        monitor.setLastCheckedAt(Instant.parse("2026-10-07T08:00:00Z"));
        return monitor;
    }
}
