package com.djmahirnationtv.status.backend.incident;

import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:incident-tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class IncidentTests {
    @Autowired IncidentRepository incidents;
    @Autowired IncidentService service;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;

    @BeforeEach
    void prepare() {
        incidents.deleteAll();
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
}
