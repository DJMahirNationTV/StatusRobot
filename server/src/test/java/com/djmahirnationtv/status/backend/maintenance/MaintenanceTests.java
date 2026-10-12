package com.djmahirnationtv.status.backend.maintenance;

import com.djmahirnationtv.status.backend.auth.AppUser;
import com.djmahirnationtv.status.backend.auth.UserRepository;
import com.djmahirnationtv.status.backend.monitor.MonitorService;
import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingExecutionService;
import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import com.djmahirnationtv.status.backend.statuspage.StatusPageRepository;
import com.djmahirnationtv.status.backend.statuspage.StatusPageService;
import com.djmahirnationtv.status.backend.team.TeamMember;
import com.djmahirnationtv.status.backend.team.TeamMemberRepository;
import com.djmahirnationtv.status.backend.team.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:maintenance-tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaintenanceTests {
    @Autowired MockMvc mvc;
    @Autowired MaintenanceService maintenance;
    @Autowired MaintenanceRepository windows;
    @Autowired MonitorRepository monitors;
    @Autowired MonitorService monitorService;
    @Autowired StatusPageService statusPages;
    @Autowired StatusPageRepository pages;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;
    private AppUser owner;
    private AppUser other;
    private Monitor website;
    private Monitor api;
    private Monitor otherWebsite;
    private Instant startsAt;
    private Instant endsAt;

    @BeforeEach
    void prepare() {
        windows.deleteAll();
        pages.deleteAll();
        members.deleteAll();
        monitors.deleteAll();
        users.deleteAll();
        owner = users.saveAndFlush(new AppUser("owner@example.com", "unused", "local", null));
        other = users.saveAndFlush(new AppUser("other@example.com", "unused", "local", null));
        website = monitor("Website", owner.getId());
        api = monitor("API", owner.getId());
        otherWebsite = monitor("Other website", other.getId());
        startsAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(7200);
        endsAt = startsAt.plusSeconds(3600);
    }

    private Monitor monitor(String name, Long ownerId) {
        var monitor = new Monitor(name, "https://example.com", "GET", 60, 5);
        monitor.setOwnerId(ownerId);
        monitor.setStatus(MonitorStatus.UP);
        return monitors.saveAndFlush(monitor);
    }

    private Maintenance window(Instant start, Instant end, List<Monitor> selected, boolean published) {
        var window = new Maintenance(owner.getId());
        window.setTitle("Database update");
        window.setDescription("Some scheduled work.");
        window.setStartsAt(start);
        window.setEndsAt(end);
        window.setPublished(published);
        window.getMonitors().addAll(selected);
        return windows.saveAndFlush(window);
    }

    private String input(Instant start, Instant end, List<Long> ids) {
        return """
                {"title":"Database update","description":"Some scheduled work.",
                 "startsAt":"%s","endsAt":"%s","monitorIds":%s,"published":false}
                """.formatted(start, end, ids);
    }

    @Test
    void ownerCanScheduleAndEditSeveralMonitors() throws Exception {
        String settings = input(startsAt, endsAt, List.of(website.getId(), api.getId()));
        mvc.perform(post("/api/maintenance").with(user(owner.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.monitorIds.length()").value(2));
        var saved = maintenance.mine(owner.getId(), 0).maintenance().getFirst();
        String changed = input(startsAt.plusSeconds(60), endsAt.plusSeconds(60), List.of(api.getId()))
                .replace("Database update", "  API update  ").replace("Some scheduled work.", "  Short interruption.  ");
        mvc.perform(put("/api/maintenance/" + saved.id()).with(user(owner.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("API update"))
                .andExpect(jsonPath("$.description").value("Short interruption."))
                .andExpect(jsonPath("$.monitorIds[0]").value(api.getId()));
        assertThat(maintenance.get(saved.id(), owner.getId()).startsAt()).isEqualTo(startsAt.plusSeconds(60));
    }

    @Test
    void datesAndSelectedMonitorsMustBeValid() throws Exception {
        String valid = input(startsAt, endsAt, List.of(website.getId()));
        List<String> invalid = List.of(
                input(Instant.now().minusSeconds(60), endsAt, List.of(website.getId())),
                input(startsAt, startsAt, List.of(website.getId())),
                input(startsAt, startsAt.minusSeconds(60), List.of(website.getId())),
                input(startsAt, endsAt, List.of()),
                input(startsAt, endsAt, List.of(website.getId(), website.getId())),
                input(startsAt, endsAt, List.of(999999L)),
                input(startsAt, endsAt, List.of(-1L)),
                input(startsAt, endsAt, List.of(website.getId(), otherWebsite.getId())),
                valid.replace("Database update", " "),
                valid.replace("Database update", "x".repeat(101)),
                valid.replace("Some scheduled work.", "x".repeat(3001)),
                valid.replace("\"" + startsAt + "\"", "null"),
                valid.replace("\"" + endsAt + "\"", "null"),
                valid.replace(startsAt.toString(), "not-a-date"),
                valid.replace("\"monitorIds\":" + List.of(website.getId()), "\"monitorIds\":null"),
                input(startsAt, endsAt, java.util.Collections.nCopies(31, website.getId()))
        );
        for (String settings : invalid) {
            mvc.perform(post("/api/maintenance").with(user(owner.getId().toString())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(settings))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
        assertThat(windows.count()).isZero();
    }

    @Test
    void acceptsTimezoneOffsetsAndStoresTheSameUtcTime() throws Exception {
        String settings = input(startsAt, endsAt, List.of(website.getId()))
                .replace(startsAt.toString(), startsAt.atOffset(java.time.ZoneOffset.ofHours(2)).toString())
                .replace(endsAt.toString(), endsAt.atOffset(java.time.ZoneOffset.ofHours(2)).toString());
        mvc.perform(post("/api/maintenance").with(user(owner.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(settings)).andExpect(status().isCreated());
        var saved = maintenance.mine(owner.getId(), 0).maintenance().getFirst();
        assertThat(saved.startsAt()).isEqualTo(startsAt);
        assertThat(saved.endsAt()).isEqualTo(endsAt);
    }

    @Test
    void privateEndpointsRequireLoginOwnershipAndCsrf() throws Exception {
        var saved = window(startsAt, endsAt, List.of(website), false);
        String path = "/api/maintenance/" + saved.getId();
        String settings = input(startsAt, endsAt, List.of(website.getId()));
        mvc.perform(get("/api/maintenance/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/maintenance").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/maintenance").with(user(owner.getId().toString())).contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(owner.getId().toString())).contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path + "/cancel").with(user(owner.getId().toString()))).andExpect(status().isForbidden());
        mvc.perform(patch(path + "/publication").with(user(owner.getId().toString()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(user(owner.getId().toString()))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user(other.getId().toString()))).andExpect(status().isNotFound());
        mvc.perform(put(path).with(user(other.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(settings)).andExpect(status().isNotFound());
        mvc.perform(patch(path + "/cancel").with(user(other.getId().toString())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(patch(path + "/publication").with(user(other.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}")).andExpect(status().isNotFound());
        mvc.perform(delete(path).with(user(other.getId().toString())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/maintenance/mine").with(user(other.getId().toString()))).andExpect(jsonPath("$.maintenance").isEmpty());
        mvc.perform(get("/api/maintenance/999999").with(user(owner.getId().toString()))).andExpect(status().isNotFound());
        assertThat(windows.count()).isEqualTo(1);
    }

    @Test
    void editorAccessToMonitorsDoesNotGiveMaintenanceOwnership() throws Exception {
        var invitation = teams.invite(owner.getId(), other.getEmail(), TeamMember.Role.EDITOR);
        teams.accept(invitation.id(), other.getId());
        var saved = window(startsAt, endsAt, List.of(website), false);
        mvc.perform(get("/api/maintenance/" + saved.getId()).with(user(other.getId().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/maintenance").with(user(other.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(input(startsAt, endsAt, List.of(website.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusAndActiveChecksUseTheStartAndEndBoundaries() {
        Instant start = Instant.parse("2030-01-01T10:00:00Z");
        Instant end = start.plusSeconds(3600);
        var saved = window(start, end, List.of(website), false);
        assertThat(saved.statusAt(start.minusSeconds(1))).isEqualTo(Maintenance.Status.SCHEDULED);
        assertThat(saved.statusAt(start)).isEqualTo(Maintenance.Status.IN_PROGRESS);
        assertThat(saved.statusAt(end)).isEqualTo(Maintenance.Status.COMPLETED);
        assertThat(maintenance.isActive(website.getId(), start.minusSeconds(1))).isFalse();
        assertThat(maintenance.isActive(website.getId(), start)).isTrue();
        assertThat(maintenance.isActive(website.getId(), end)).isFalse();
        assertThat(maintenance.isActive(api.getId(), start)).isFalse();
        var afterRestart = new MaintenanceService(windows, monitors);
        assertThat(afterRestart.isActive(website.getId(), start)).isTrue();
        saved.setCancelled(true);
        windows.saveAndFlush(saved);
        assertThat(saved.statusAt(start)).isEqualTo(Maintenance.Status.CANCELLED);
        assertThat(maintenance.isActive(website.getId(), start)).isFalse();
    }

    @Test
    void overlappingWindowsKeepTheMonitorSkippedUntilBothEndOrAreCancelled() {
        var first = window(startsAt, endsAt, List.of(website), false);
        var second = window(startsAt.plusSeconds(60), endsAt.plusSeconds(60), List.of(website), false);
        Instant duringBoth = startsAt.plusSeconds(120);
        maintenance.cancel(first.getId(), owner.getId());
        assertThat(maintenance.isActive(website.getId(), duringBoth)).isTrue();
        maintenance.cancel(second.getId(), owner.getId());
        assertThat(maintenance.isActive(website.getId(), duringBoth)).isFalse();
    }

    @Test
    void ongoingMaintenanceCanBeCancelledButNotEditedOrDeletedDirectly() throws Exception {
        var active = window(Instant.now().minusSeconds(60), endsAt, List.of(website), false);
        String path = "/api/maintenance/" + active.getId();
        mvc.perform(get(path).with(user(owner.getId().toString()))).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(put(path).with(user(owner.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(input(startsAt, endsAt, List.of(website.getId()))))
                .andExpect(status().isConflict());
        mvc.perform(delete(path).with(user(owner.getId().toString())).with(csrf())).andExpect(status().isConflict());
        mvc.perform(patch(path + "/cancel").with(user(owner.getId().toString())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(patch(path + "/cancel").with(user(owner.getId().toString())).with(csrf())).andExpect(status().isOk());
        assertThat(maintenance.isActive(website.getId(), Instant.now())).isFalse();
        mvc.perform(delete(path).with(user(owner.getId().toString())).with(csrf())).andExpect(status().isNoContent());
        assertThat(monitors.existsById(website.getId())).isTrue();
    }

    @Test
    void completedAndCancelledMaintenanceCannotBeRescheduled() throws Exception {
        var completed = window(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), List.of(website), false);
        var cancelled = window(startsAt, endsAt, List.of(website), false);
        maintenance.cancel(cancelled.getId(), owner.getId());
        for (Long id : List.of(completed.getId(), cancelled.getId())) {
            mvc.perform(put("/api/maintenance/" + id).with(user(owner.getId().toString())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(input(startsAt, endsAt, List.of(website.getId()))))
                    .andExpect(status().isConflict());
        }
        mvc.perform(get("/api/maintenance/" + completed.getId()).with(user(owner.getId().toString())))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(patch("/api/maintenance/" + completed.getId() + "/cancel").with(user(owner.getId().toString())).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/maintenance/" + completed.getId()).with(user(owner.getId().toString())).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void publicNoticesRequirePublicationAndMatchThePageOwnerAndMonitors() throws Exception {
        statusPages.save(null, new StatusPageService.StatusPageRequest("Status", "maintenance-status", "", List.of(website.getId()), true), owner.getId());
        var visible = window(startsAt, endsAt, List.of(website, api), true);
        window(startsAt, endsAt, List.of(website), false);
        window(startsAt, endsAt, List.of(api), true);
        window(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), List.of(website), true);
        // Save a mismatched owner to check that public queries do not leak their notice.
        var foreign = new Maintenance(other.getId());
        foreign.setTitle("Another owner's work");
        foreign.setStartsAt(startsAt);
        foreign.setEndsAt(endsAt);
        foreign.setPublished(true);
        foreign.getMonitors().add(website);
        windows.saveAndFlush(foreign);
        mvc.perform(get("/api/status-pages/maintenance-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenance.length()").value(1))
                .andExpect(jsonPath("$.maintenance[0].id").value(visible.getId()))
                .andExpect(jsonPath("$.maintenance[0].monitorIds.length()").value(1))
                .andExpect(jsonPath("$.maintenance[0].monitorIds[0]").value(website.getId()))
                .andExpect(jsonPath("$.maintenance[0].ownerId").doesNotExist());
        mvc.perform(get("/api/status-pages/default")).andExpect(jsonPath("$.maintenance[0].id").value(visible.getId()));
        mvc.perform(patch("/api/maintenance/" + visible.getId() + "/publication").with(user(owner.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"published\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/status-pages/maintenance-status")).andExpect(jsonPath("$.maintenance").isEmpty());
        maintenance.publish(visible.getId(), new MaintenanceService.PublicationRequest(true), owner.getId());
        maintenance.cancel(visible.getId(), owner.getId());
        mvc.perform(get("/api/status-pages/maintenance-status")).andExpect(jsonPath("$.maintenance[0].status").value("CANCELLED"));
        statusPages.save(null, new StatusPageService.StatusPageRequest("Empty", "no-monitors", "", List.of(), false), owner.getId());
        mvc.perform(get("/api/status-pages/no-monitors")).andExpect(jsonPath("$.maintenance").isEmpty());
        mvc.perform(patch("/api/maintenance/" + visible.getId() + "/publication").with(user(owner.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingMonitorsRemovesTheirLinksAndCancelsAnEmptyWindow() {
        var saved = window(Instant.now().minusSeconds(60), endsAt, List.of(website, api), false);
        monitorService.deleteMonitor(website.getId(), owner.getId());
        assertThat(maintenance.get(saved.getId(), owner.getId()).monitorIds()).containsExactly(api.getId());
        assertThat(maintenance.isActive(api.getId(), Instant.now())).isTrue();
        monitorService.deleteMonitor(api.getId(), owner.getId());
        var remaining = maintenance.get(saved.getId(), owner.getId());
        assertThat(remaining.monitorIds()).isEmpty();
        assertThat(remaining.status()).isEqualTo(Maintenance.Status.CANCELLED);
        verify(pings).deleteByMonitorId(website.getId());
        verify(pings).deleteByMonitorId(api.getId());
    }

    @Test
    void deletingTheLastMonitorDoesNotChangeCompletedHistoryIntoACancellation() {
        var completed = window(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), List.of(website), false);
        monitorService.deleteMonitor(website.getId(), owner.getId());
        var saved = maintenance.get(completed.getId(), owner.getId());
        assertThat(saved.status()).isEqualTo(Maintenance.Status.COMPLETED);
        assertThat(saved.monitorIds()).isEmpty();
    }

    @Test
    void schedulerSkipsActiveMaintenanceAndResumesAfterCancellationWithoutUnpausingMonitors() {
        var active = window(Instant.now().minusSeconds(60), endsAt, List.of(website), false);
        api.setStatus(MonitorStatus.PAUSED);
        monitors.saveAndFlush(api);
        var execution = mock(PingExecutionService.class);
        var polling = new PingScheduler(monitors, execution, maintenance);
        polling.schedulePings();
        verify(execution, never()).ping(argThat(m -> m.getId().equals(website.getId())));
        verify(execution, never()).ping(argThat(m -> m.getId().equals(api.getId())));
        verify(execution).ping(argThat(m -> m.getId().equals(otherWebsite.getId())));
        maintenance.cancel(active.getId(), owner.getId());
        polling.schedulePings();
        verify(execution).ping(argThat(m -> m.getId().equals(website.getId())));
        verify(execution, never()).ping(argThat(m -> m.getId().equals(api.getId())));
        assertThat(monitors.findById(website.getId()).orElseThrow().getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(monitors.findById(api.getId()).orElseThrow().getStatus()).isEqualTo(MonitorStatus.PAUSED);
    }

    @Test
    void schedulerResumesWhenTheSavedEndTimeHasPassed() {
        var active = window(Instant.now().minusSeconds(7200), endsAt, List.of(website), false);
        var execution = mock(PingExecutionService.class);
        var polling = new PingScheduler(monitors, execution, maintenance);
        polling.schedulePings();
        verify(execution, never()).ping(argThat(m -> m.getId().equals(website.getId())));
        // Move the saved end time into the past instead of waiting during a test.
        active.setEndsAt(Instant.now().minusSeconds(60));
        windows.saveAndFlush(active);
        polling.schedulePings();
        verify(execution).ping(argThat(m -> m.getId().equals(website.getId())));
        assertThat(maintenance.get(active.getId(), owner.getId()).status()).isEqualTo(Maintenance.Status.COMPLETED);
    }

    @Test
    void listsTwentyWindowsPerPageAndRejectsInvalidPageNumbers() throws Exception {
        for (int i = 0; i < 23; i++) window(startsAt.plusSeconds(i), endsAt.plusSeconds(i), List.of(website), false);
        mvc.perform(get("/api/maintenance/mine").with(user(owner.getId().toString())))
                .andExpect(jsonPath("$.maintenance.length()").value(20)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(23));
        mvc.perform(get("/api/maintenance/mine?page=1").with(user(owner.getId().toString())))
                .andExpect(jsonPath("$.maintenance.length()").value(3));
        mvc.perform(get("/api/maintenance/mine?page=-1").with(user(owner.getId().toString()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/maintenance/mine?page=100001").with(user(owner.getId().toString()))).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentChangesCannotOverwriteANewerSchedule() {
        var saved = window(startsAt, endsAt, List.of(website), false);
        var first = windows.findById(saved.getId()).orElseThrow();
        var second = windows.findById(saved.getId()).orElseThrow();
        first.setTitle("Updated schedule");
        windows.saveAndFlush(first);
        second.setTitle("Old schedule");
        assertThatThrownBy(() -> windows.saveAndFlush(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(maintenance.get(saved.getId(), owner.getId()).title()).isEqualTo("Updated schedule");
    }
}
