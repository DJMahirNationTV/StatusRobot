package com.djmahirnationtv.status.backend.team;

import com.djmahirnationtv.status.backend.auth.AppUser;
import com.djmahirnationtv.status.backend.auth.UserRepository;
import com.djmahirnationtv.status.backend.integration.DiscordIntegration;
import com.djmahirnationtv.status.backend.integration.DiscordIntegrationRepository;
import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingScheduler;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:team-tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamTests {
    private static final String MONITOR = """
            {"name":"Team website","url":"https://example.com","httpMethod":"GET",
            "intervalSeconds":60,"timeoutSeconds":5}
            """;

    @Autowired MockMvc mvc;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;
    @Autowired MonitorRepository monitors;
    @Autowired DiscordIntegrationRepository integrations;
    @MockitoBean PingScheduler scheduler;
    @MockitoBean PingLogRepository pings;
    private AppUser owner;
    private AppUser member;
    private AppUser stranger;
    private Monitor monitor;

    @BeforeEach
    void prepare() {
        members.deleteAll();
        monitors.deleteAll();
        integrations.deleteAll();
        users.deleteAll();
        owner = users.saveAndFlush(new AppUser("owner@example.com", "unused", "local", null));
        member = users.saveAndFlush(new AppUser("member@example.com", "unused", "local", null));
        stranger = users.saveAndFlush(new AppUser("stranger@example.com", "unused", "local", null));
        monitor = new Monitor("Owner website", "https://example.com", "GET", 60, 5);
        monitor.setOwnerId(owner.getId());
        monitor = monitors.saveAndFlush(monitor);
    }

    private TeamService.Member invitation(TeamMember.Role role) {
        return teams.invite(owner.getId(), member.getEmail(), role);
    }

    @Test
    void invitationRequiresAcceptanceAndOnlyTheRecipientCanAccept() throws Exception {
        mvc.perform(post("/api/team-members").with(user(owner.getId().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"MEMBER@EXAMPLE.COM\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accepted").value(false));
        Long id = members.findByOwnerIdOrderByIdDesc(owner.getId()).getFirst().getId();
        mvc.perform(get("/api/team-members").with(user(member.getId().toString())))
                .andExpect(jsonPath("$.invitations[0].email").value(owner.getEmail()))
                .andExpect(jsonPath("$.workspaces.length()").value(1));
        mvc.perform(get("/api/monitors/mine?workspaceId=" + owner.getId()).with(user(member.getId().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/team-members/" + id + "/accept").with(user(stranger.getId().toString())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/team-members/" + id + "/accept").with(user(owner.getId().toString())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/team-members/" + id + "/accept").with(user(member.getId().toString())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/team-members").with(user(member.getId().toString())))
                .andExpect(jsonPath("$.invitations.length()").value(0))
                .andExpect(jsonPath("$.workspaces.length()").value(2));
        mvc.perform(get("/api/monitors/mine?workspaceId=" + owner.getId()).with(user(member.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Owner website"));
        mvc.perform(get("/api/monitors/mine").with(user(member.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void viewerCannotCreateEditPauseDeleteOrReadIntegrationSelections() throws Exception {
        teams.accept(invitation(TeamMember.Role.VIEWER).id(), member.getId());
        String path = "/api/monitors/" + monitor.getId();
        mvc.perform(post("/api/monitors?workspaceId=" + owner.getId()).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(MONITOR)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(MONITOR)).andExpect(status().isForbidden());
        mvc.perform(patch(path + "/toggle-pause").with(user(member.getId().toString())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path).with(user(member.getId().toString())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/integrations/monitors/" + monitor.getId()).with(user(member.getId().toString())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/monitors/workspace/integrations?workspaceId=" + owner.getId()).with(user(member.getId().toString())))
                .andExpect(status().isForbidden());
        assertThat(monitors.count()).isEqualTo(1);
    }

    @Test
    void editorCanManageMonitorsWithoutChangingTheirOwner() throws Exception {
        teams.accept(invitation(TeamMember.Role.EDITOR).id(), member.getId());
        mvc.perform(post("/api/monitors?workspaceId=" + owner.getId()).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(MONITOR)).andExpect(status().isCreated());
        assertThat(monitors.findByOwnerIdOrderByCreatedAtDesc(owner.getId())).hasSize(2);
        assertThat(monitors.findByOwnerIdOrderByCreatedAtDesc(member.getId())).isEmpty();
        String path = "/api/monitors/" + monitor.getId();
        mvc.perform(put(path).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(MONITOR)).andExpect(status().isOk());
        mvc.perform(patch(path + "/toggle-pause").with(user(member.getId().toString())).with(csrf())).andExpect(status().isOk());
        assertThat(monitors.findById(monitor.getId()).orElseThrow().getOwnerId()).isEqualTo(owner.getId());
        mvc.perform(delete(path).with(user(member.getId().toString())).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void onlyTheOwnerCanChangeRolesAndRemovalRevokesAccess() throws Exception {
        Long id = invitation(TeamMember.Role.EDITOR).id();
        teams.accept(id, member.getId());
        mvc.perform(patch("/api/team-members/" + id).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/team-members/" + id).with(user(stranger.getId().toString())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/team-members/" + id).with(user(owner.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/monitors/" + monitor.getId()).with(user(member.getId().toString())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/team-members/" + id).with(user(owner.getId().toString())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/monitors/mine?workspaceId=" + owner.getId()).with(user(member.getId().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCanDeclineOrLeaveAndCanBeInvitedAgain() throws Exception {
        Long id = invitation(TeamMember.Role.VIEWER).id();
        mvc.perform(delete("/api/team-members/" + id).with(user(member.getId().toString())).with(csrf()))
                .andExpect(status().isNoContent());
        Long next = invitation(TeamMember.Role.VIEWER).id();
        teams.accept(next, member.getId());
        mvc.perform(delete("/api/team-members/" + next).with(user(member.getId().toString())).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(members.count()).isZero();
        assertThat(monitors.existsById(monitor.getId())).isTrue();
    }

    @Test
    void invitationsValidateAccountRoleAndDuplicates() throws Exception {
        for (String input : new String[]{"{\"email\":\"missing@example.com\",\"role\":\"VIEWER\"}",
                "{\"email\":\"owner@example.com\",\"role\":\"EDITOR\"}",
                "{\"email\":\"invalid\",\"role\":\"VIEWER\"}",
                "{\"email\":\"member@example.com\",\"role\":null}",
                "{\"email\":\"member@example.com\",\"role\":\"OWNER\"}"}) {
            mvc.perform(post("/api/team-members").with(user(owner.getId().toString())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
        }
        invitation(TeamMember.Role.VIEWER);
        mvc.perform(post("/api/team-members").with(user(owner.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void teamAndMonitorActionsRequireLoginCsrfAndAnAcceptedMembership() throws Exception {
        Long id = invitation(TeamMember.Role.EDITOR).id();
        mvc.perform(get("/api/team-members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/monitors/workspace/integrations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/team-members/" + id + "/accept").with(user(member.getId().toString())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/team-members/" + id).with(user(owner.getId().toString())))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/monitors/" + monitor.getId()).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(MONITOR)).andExpect(status().isNotFound());
        mvc.perform(post("/api/monitors?workspaceId=" + owner.getId()).with(user(stranger.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(MONITOR)).andExpect(status().isNotFound());
        assertThat(members.findById(id).orElseThrow().isAccepted()).isFalse();
    }

    @Test
    void editorCanSelectOwnersIntegrationsButCannotManageThemOrSelectTheirOwn() throws Exception {
        var ownerChannel = new DiscordIntegration();
        ownerChannel.setOwnerId(owner.getId());
        ownerChannel.setName("Owner channel");
        ownerChannel.setEncryptedWebhook("encrypted-placeholder");
        ownerChannel = integrations.saveAndFlush(ownerChannel);
        var memberChannel = new DiscordIntegration();
        memberChannel.setOwnerId(member.getId());
        memberChannel.setName("Member channel");
        memberChannel.setEncryptedWebhook("encrypted-placeholder");
        memberChannel = integrations.saveAndFlush(memberChannel);
        teams.accept(invitation(TeamMember.Role.EDITOR).id(), member.getId());
        mvc.perform(get("/api/monitors/workspace/integrations?workspaceId=" + owner.getId()).with(user(member.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.integrations[0].name").value("Owner channel"))
                .andExpect(jsonPath("$.integrations[0].encryptedWebhook").doesNotExist());
        String update = MONITOR.strip().replace("\"timeoutSeconds\":5", "\"timeoutSeconds\":5,\"integrationIds\":[" + ownerChannel.getId() + "]");
        mvc.perform(put("/api/monitors/" + monitor.getId()).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
        String invalid = update.replace("\"integrationIds\":[" + ownerChannel.getId() + "]", "\"integrationIds\":[" + memberChannel.getId() + "]");
        mvc.perform(put("/api/monitors/" + monitor.getId()).with(user(member.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/integrations/" + ownerChannel.getId()).with(user(member.getId().toString())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitationLimitIncludesPendingMembers() throws Exception {
        for (int i = 0; i < 50; i++) {
            var account = users.saveAndFlush(new AppUser("teammate" + i + "@example.com", "unused", "local", null));
            teams.invite(owner.getId(), account.getEmail(), TeamMember.Role.VIEWER);
        }
        mvc.perform(post("/api/team-members").with(user(owner.getId().toString())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("A workspace can have up to 50 members, including pending invitations."));
        assertThat(members.count()).isEqualTo(50);
    }
}
