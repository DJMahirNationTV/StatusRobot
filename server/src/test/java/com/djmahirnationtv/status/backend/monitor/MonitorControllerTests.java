package com.djmahirnationtv.status.backend.monitor;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:monitor-tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitorControllerTests {
    private static final String SETTINGS = """
            {"name":"Updated website", "url":"https://djmahirnationtv.com/health", "httpMethod":"HEAD",
             "intervalSeconds":120, "timeoutSeconds":8}
            """;

    @Autowired MockMvc mvc;
    @Autowired MonitorRepository monitors;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;
    private Monitor owned;

    @BeforeEach
    void prepare() {
        monitors.deleteAll();
        owned = new Monitor("My website", "https://djmahirnationtv.com", "GET", 60, 5);
        owned.setOwnerId(1L);
        owned = monitors.saveAndFlush(owned);
    }

    @Test
    void dashboardListsOnlyTheSignedInUsersMonitors() throws Exception {
        var other = new Monitor("another random site", "https://youtube.com", "GET", 60, 5);
        other.setOwnerId(2L);
        monitors.saveAndFlush(other);

        mvc.perform(get("/api/monitors/mine").with(user("1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("My website"));
        mvc.perform(get("/api/monitors/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/monitors")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createAssignsOwnershipAndUpdateSavesAllSettings() throws Exception {
        mvc.perform(post("/api/monitors").with(user("1")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.httpMethod").value("HEAD"));
        assertThat(monitors.findByOwnerIdOrderByCreatedAtDesc(1L)).hasSize(2);

        mvc.perform(put("/api/monitors/" + owned.getId()).with(user("1")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated website"))
                .andExpect(jsonPath("$.url").value("https://djmahirnationtv.com/health"))
                .andExpect(jsonPath("$.httpMethod").value("HEAD"))
                .andExpect(jsonPath("$.intervalSeconds").value(120))
                .andExpect(jsonPath("$.timeoutSeconds").value(8))
                .andExpect(jsonPath("$.status").value("PAUSED"));
        assertThat(monitors.findById(owned.getId()).orElseThrow().getName()).isEqualTo("Updated website");
    }

    @Test
    void editingRequiresLoginOwnershipAndCsrf() throws Exception {
        String path = "/api/monitors/" + owned.getId();
        mvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(user("1")).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("2")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/monitors/999999").with(user("1")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SETTINGS))
                .andExpect(status().isNotFound());
        assertThat(monitors.findById(owned.getId()).orElseThrow().getName()).isEqualTo("My website");
    }

    @Test
    void rejectsInvalidSettingsOnCreateAndUpdate() throws Exception {
        String[] invalidSettings = {
                SETTINGS.replace("Updated website", " "),
                SETTINGS.replace("https://", "ftp://"),
                SETTINGS.replace("HEAD", "DELETE"),
                SETTINGS.replace("120", "0"),
                SETTINGS.replace("120", "86401"),
                SETTINGS.replace("\"timeoutSeconds\":8", "\"timeoutSeconds\":31"),
                SETTINGS.replace("\"timeoutSeconds\":8", "\"timeoutSeconds\":0"),
                SETTINGS.replace("120", "10").replace("\"timeoutSeconds\":8", "\"timeoutSeconds\":11")
        };
        for (String invalid : invalidSettings) {
            mvc.perform(post("/api/monitors").with(user("1")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
            mvc.perform(put("/api/monitors/" + owned.getId()).with(user("1")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(monitors.count()).isEqualTo(1);
    }

    @Test
    void ownerCanResumePauseAndDeleteIncludingCheckHistory() throws Exception {
        String path = "/api/monitors/" + owned.getId();
        mvc.perform(patch(path + "/toggle-pause").with(user("1")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(patch(path + "/toggle-pause").with(user("1")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(delete(path).with(user("1")).with(csrf())).andExpect(status().isNoContent());
        assertThat(monitors.existsById(owned.getId())).isFalse();
        verify(pings).deleteByMonitorId(owned.getId());
    }

    @Test
    void anOldPingCannotOverwriteNewMonitorSettings() {
        Monitor oldPingSnapshot = monitors.findById(owned.getId()).orElseThrow();
        Monitor edited = monitors.findById(owned.getId()).orElseThrow();
        edited.setName("New name");
        monitors.saveAndFlush(edited);

        oldPingSnapshot.setStatus(MonitorStatus.UP);
        assertThatThrownBy(() -> monitors.saveAndFlush(oldPingSnapshot))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(monitors.findById(owned.getId()).orElseThrow().getName()).isEqualTo("New name");
    }
}
