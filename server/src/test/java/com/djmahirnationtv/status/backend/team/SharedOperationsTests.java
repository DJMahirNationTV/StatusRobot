package com.djmahirnationtv.status.backend.team;

import com.djmahirnationtv.status.backend.auth.AppUser;
import com.djmahirnationtv.status.backend.auth.UserRepository;
import com.djmahirnationtv.status.backend.incident.*;
import com.djmahirnationtv.status.backend.maintenance.*;
import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import com.djmahirnationtv.status.backend.statuspage.StatusPageRepository;
import com.djmahirnationtv.status.backend.statuspage.StatusPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:shared-operations;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedOperationsTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TeamMemberRepository members;
    @Autowired TeamService teams;
    @Autowired MonitorRepository monitors;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentService incidentService;
    @Autowired MaintenanceRepository windows;
    @Autowired MaintenanceService maintenance;
    @Autowired StatusPageRepository pages;
    @Autowired StatusPageService statusPages;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;
    @MockitoBean IncidentAnalysisService analysis;
    private AppUser owner, editor, viewer, pending, stranger;
    private Monitor monitor, otherMonitor;
    private Long editorMembership;

    @BeforeEach
    void prepare() {
        windows.deleteAll();
        pages.deleteAll();
        incidents.deleteAll();
        members.deleteAll();
        monitors.deleteAll();
        users.deleteAll();
        owner = account("owner");
        editor = account("editor");
        viewer = account("viewer");
        pending = account("pending");
        stranger = account("stranger");
        editorMembership = teams.invite(owner.getId(), editor.getEmail(), TeamMember.Role.EDITOR).id();
        teams.accept(editorMembership, editor.getId());
        teams.accept(teams.invite(owner.getId(), viewer.getEmail(), TeamMember.Role.VIEWER).id(), viewer.getId());
        teams.invite(owner.getId(), pending.getEmail(), TeamMember.Role.EDITOR);
        monitor = monitor(owner.getId());
        otherMonitor = monitor(stranger.getId());
    }

    private AppUser account(String name) {
        return users.saveAndFlush(new AppUser(name + "@example.com", "unused", "local", null));
    }

    private Monitor monitor(Long ownerId) {
        var monitor = new Monitor("Website", "https://example.com", "GET", 60, 5);
        monitor.setOwnerId(ownerId);
        return monitors.saveAndFlush(monitor);
    }

    private IncidentService.IncidentResponse incident() {
        return incidentService.create(new IncidentService.CreateRequest(monitor.getId(), "Website outage", "Investigating."), owner.getId());
    }

    private String incidentBody(Long monitorId) {
        return "{\"monitorId\":" + monitorId + ",\"title\":\"Website outage\",\"message\":\"Investigating.\"}";
    }

    private String maintenanceBody(List<Long> ids) {
        Instant start = Instant.now().plusSeconds(7200);
        return """
                {"title":"Server update","description":"Planned work.","startsAt":"%s",
                "endsAt":"%s","monitorIds":%s,"published":false}
                """.formatted(start, start.plusSeconds(3600), ids);
    }

    private MaintenanceService.Response window() {
        Instant start = Instant.now().plusSeconds(7200);
        return maintenance.save(null, new MaintenanceService.SaveRequest("Server update", "Planned work.",
                start, start.plusSeconds(3600), List.of(monitor.getId()), false), owner.getId());
    }

    @Test
    void editorCreatesUpdatesPublishesAndResolvesAnIncidentForTheOwner() throws Exception {
        mvc.perform(post("/api/incidents").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(incidentBody(monitor.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workspaceId").value(owner.getId()));
        var saved = incidentService.mine(owner.getId(), "all", 0).incidents().getFirst();
        assertThat(incidents.findById(saved.id()).orElseThrow().getOwnerId()).isEqualTo(owner.getId());
        mvc.perform(get("/api/incidents").param("workspaceId", owner.getId().toString()).with(user(editor.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/incidents").with(user(editor.getId().toString()))).andExpect(jsonPath("$.incidents").isEmpty());
        mvc.perform(post("/api/incidents/" + saved.id() + "/updates").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"IDENTIFIED\",\"message\":\"Found the issue.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("IDENTIFIED"));
        mvc.perform(patch("/api/incidents/" + saved.id()).with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Website outage\",\"published\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        mvc.perform(post("/api/incidents/" + saved.id() + "/updates").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"RESOLVED\",\"message\":\"Recovered.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("RESOLVED"));
    }

    @Test
    void editorSchedulesEditsPublishesCancelsAndDeletesMaintenance() throws Exception {
        mvc.perform(post("/api/maintenance").param("workspaceId", owner.getId().toString()).with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(maintenanceBody(List.of(monitor.getId()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workspaceId").value(owner.getId()));
        var saved = maintenance.mine(owner.getId(), 0).maintenance().getFirst();
        String path = "/api/maintenance/" + saved.id();
        mvc.perform(get("/api/maintenance/mine").param("workspaceId", owner.getId().toString()).with(user(editor.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/maintenance/mine").with(user(editor.getId().toString()))).andExpect(jsonPath("$.maintenance").isEmpty());
        mvc.perform(put(path).with(user(editor.getId().toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(maintenanceBody(List.of(monitor.getId())).replace("Server update", "Updated schedule")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated schedule"));
        mvc.perform(patch(path + "/publication").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        mvc.perform(patch(path + "/cancel").with(user(editor.getId().toString())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete(path).with(user(editor.getId().toString())).with(csrf())).andExpect(status().isNoContent());
        assertThat(monitors.existsById(monitor.getId())).isTrue();
    }

    @Test
    void viewerCanReadSharedRecordsButCannotWriteOrAskForAnalysis() throws Exception {
        var incident = incident();
        var window = window();
        String incidentPath = "/api/incidents/" + incident.id();
        String maintenancePath = "/api/maintenance/" + window.id();
        mvc.perform(get(incidentPath).with(user(viewer.getId().toString()))).andExpect(status().isOk());
        mvc.perform(get(maintenancePath).with(user(viewer.getId().toString()))).andExpect(status().isOk());
        for (String path : List.of("/api/incidents", "/api/maintenance/mine")) {
            mvc.perform(get(path).param("workspaceId", owner.getId().toString()).with(user(viewer.getId().toString()))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/incidents").with(user(viewer.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(incidentBody(monitor.getId()))).andExpect(status().isForbidden());
        mvc.perform(post(incidentPath + "/updates").with(user(viewer.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"IDENTIFIED\",\"message\":\"Update.\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(incidentPath).with(user(viewer.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Outage\",\"published\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(incidentPath + "/analysis").with(user(viewer.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/maintenance").param("workspaceId", owner.getId().toString()).with(user(viewer.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(maintenanceBody(List.of(monitor.getId()))))
                .andExpect(status().isForbidden());
        mvc.perform(put(maintenancePath).with(user(viewer.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(maintenanceBody(List.of(monitor.getId()))))
                .andExpect(status().isForbidden());
        for (String action : List.of("/cancel", "/publication")) {
            mvc.perform(patch(maintenancePath + action).with(user(viewer.getId().toString())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(delete(maintenancePath).with(user(viewer.getId().toString())).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(analysis);
    }

    @Test
    void pendingInvitationsAndStrangersCannotReadOrCreateSharedRecords() throws Exception {
        var incident = incident();
        var window = window();
        for (AppUser user : List.of(pending, stranger)) {
            for (String path : List.of("/api/incidents", "/api/maintenance/mine")) {
                mvc.perform(get(path).param("workspaceId", owner.getId().toString()).with(user(user.getId().toString())))
                        .andExpect(status().isNotFound());
            }
            for (String path : List.of("/api/incidents/" + incident.id(), "/api/maintenance/" + window.id())) {
                mvc.perform(get(path).with(user(user.getId().toString()))).andExpect(status().isNotFound());
            }
            mvc.perform(post("/api/incidents").with(user(user.getId().toString())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(incidentBody(monitor.getId())))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/maintenance").param("workspaceId", owner.getId().toString()).with(user(user.getId().toString())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(maintenanceBody(List.of(monitor.getId()))))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void removedMembersLoseAccessOnTheNextRequest() throws Exception {
        var incident = incident();
        var window = window();
        teams.remove(editorMembership, owner.getId());
        mvc.perform(get("/api/incidents/" + incident.id()).with(user(editor.getId().toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/maintenance/" + window.id()).with(user(editor.getId().toString()))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/maintenance/" + window.id() + "/cancel").with(user(editor.getId().toString())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/incidents/" + incident.id() + "/updates").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"IDENTIFIED\",\"message\":\"Update.\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRoleChangeStopsWritesButKeepsViewerReads() throws Exception {
        var incident = incident();
        var window = window();
        teams.changeRole(editorMembership, owner.getId(), TeamMember.Role.VIEWER);
        mvc.perform(get("/api/incidents/" + incident.id()).with(user(editor.getId().toString()))).andExpect(status().isOk());
        mvc.perform(get("/api/maintenance/" + window.id()).with(user(editor.getId().toString()))).andExpect(status().isOk());
        mvc.perform(patch("/api/maintenance/" + window.id() + "/cancel").with(user(editor.getId().toString())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/incidents/" + incident.id() + "/updates").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"IDENTIFIED\",\"message\":\"Update.\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void maintenanceCannotCombineOrMoveBetweenWorkspaces() throws Exception {
        teams.accept(teams.invite(stranger.getId(), editor.getEmail(), TeamMember.Role.EDITOR).id(), editor.getId());
        var window = window();
        mvc.perform(post("/api/maintenance").param("workspaceId", owner.getId().toString()).with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(maintenanceBody(List.of(monitor.getId(), otherMonitor.getId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/maintenance/" + window.id()).param("workspaceId", stranger.getId().toString())
                .with(user(editor.getId().toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(maintenanceBody(List.of(otherMonitor.getId())))).andExpect(status().isBadRequest());
        assertThat(maintenance.get(window.id(), owner.getId()).workspaceId()).isEqualTo(owner.getId());
        assertThat(maintenance.get(window.id(), owner.getId()).monitorIds()).containsExactly(monitor.getId());
    }

    @Test
    void editorStillNeedsCsrfAndCannotResolveAnAutomaticOutage() throws Exception {
        monitor.setStatus(MonitorStatus.DOWN);
        monitor.setLastCheckedAt(Instant.now());
        incidentService.recordCheck(monitor, 503);
        Long id = incidents.findByOpenMonitorId(monitor.getId()).orElseThrow().getId();
        mvc.perform(post("/api/incidents/" + id + "/updates").with(user(editor.getId().toString()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"IDENTIFIED\",\"message\":\"Update.\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/maintenance").param("workspaceId", owner.getId().toString()).with(user(editor.getId().toString()))
                .contentType(MediaType.APPLICATION_JSON).content(maintenanceBody(List.of(monitor.getId()))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/incidents/" + id + "/updates").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"RESOLVED\",\"message\":\"Fixed.\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editorAnalysisUsesTheEditorsRequestLimitWithoutPublishing() throws Exception {
        var incident = incident();
        when(analysis.analyze(any(), eq(editor.getId()), eq(true), eq("Test context")))
                .thenReturn(new IncidentAnalysisService.Analysis("Check the logs.", "test-model"));
        mvc.perform(post("/api/incidents/" + incident.id() + "/analysis").with(user(editor.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true,\"context\":\"Test context\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Check the logs."));
        verify(analysis).analyze(any(), eq(editor.getId()), eq(true), eq("Test context"));
        assertThat(incidentService.get(incident.id(), owner.getId()).published()).isFalse();
        assertThat(incidentService.get(incident.id(), owner.getId()).updates()).hasSize(1);
    }

    @Test
    void publishedTeamWorkAppearsOnTheOwnersStatusPageWithoutWorkspaceIds() throws Exception {
        statusPages.save(null, new StatusPageService.StatusPageRequest("Owner status", "owner-status", "", List.of(monitor.getId()), false), owner.getId());
        var incident = incidentService.create(new IncidentService.CreateRequest(monitor.getId(), "Outage", "Investigating."), editor.getId());
        incidentService.publish(incident.id(), new IncidentService.PublicationRequest("Outage", true), editor.getId());
        var window = window();
        maintenance.publish(window.id(), new MaintenanceService.PublicationRequest(true), editor.getId());
        mvc.perform(get("/api/status-pages/owner-status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents[0].id").value(incident.id()))
                .andExpect(jsonPath("$.maintenance[0].id").value(window.id()))
                .andExpect(jsonPath("$.incidents[0].workspaceId").doesNotExist())
                .andExpect(jsonPath("$.maintenance[0].workspaceId").doesNotExist());
    }
}
