package com.djmahirnationtv.status.backend.team;

import com.djmahirnationtv.status.backend.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TeamService {
    private final TeamMemberRepository members;
    private final UserRepository users;

    public TeamService(TeamMemberRepository members, UserRepository users) {
        this.members = members;
        this.users = users;
    }

    public record Member(Long id, String email, TeamMember.Role role, boolean accepted) {}
    public record Workspace(Long id, String email, String role, Long membershipId) {}
    public record Invitation(Long id, String email, TeamMember.Role role) {}
    public record Listing(List<Member> members, List<Workspace> workspaces, List<Invitation> invitations) {}

    @Transactional(readOnly = true)
    public Listing list(Long userId) {
        var user = users.findById(userId).orElseThrow(() -> notFound());
        List<Member> ownMembers = new ArrayList<>();
        for (TeamMember membership : members.findByOwnerIdOrderByIdDesc(userId)) {
            ownMembers.add(summary(membership));
        }
        List<Workspace> workspaces = new ArrayList<>();
        workspaces.add(new Workspace(userId, user.getEmail(), "OWNER", null));
        List<Invitation> invitations = new ArrayList<>();
        for (TeamMember membership : members.findByMemberIdOrderByIdDesc(userId)) {
            if (membership.isAccepted()) {
                workspaces.add(new Workspace(membership.getOwner().getId(), membership.getOwner().getEmail(), membership.getRole().name(), membership.getId()));
            } else {
                invitations.add(new Invitation(membership.getId(), membership.getOwner().getEmail(), membership.getRole()));
            }
        }
        return new Listing(ownMembers, workspaces, invitations);
    }

    @Transactional
    public Member invite(Long ownerId, String email, TeamMember.Role role) {
        // Lock the owner row so two invitations cannot pass the limit together.
        var owner = users.lockForSettingsUpdate(ownerId).orElseThrow(() -> notFound());
        var member = users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ask this person to create an account first."));
        if (ownerId.equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already own this workspace.");
        }
        if (members.existsByOwnerIdAndMemberId(ownerId, member.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This person is already invited or part of your team.");
        }
        if (members.countByOwnerId(ownerId) >= 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A workspace can have up to 50 members, including pending invitations.");
        }
        // An invitation does not give access until the other person accepts it.
        return summary(members.saveAndFlush(new TeamMember(owner, member, role)));
    }

    @Transactional
    public void accept(Long id, Long userId) {
        TeamMember membership = members.findById(id).orElseThrow(() -> notFound());
        if (!membership.getMember().getId().equals(userId)) throw notFound();
        membership.accept();
    }

    @Transactional
    public Member changeRole(Long id, Long ownerId, TeamMember.Role role) {
        TeamMember membership = members.findById(id).orElseThrow(() -> notFound());
        if (!membership.getOwner().getId().equals(ownerId)) throw notFound();
        membership.setRole(role);
        return summary(membership);
    }

    @Transactional
    public void remove(Long id, Long userId) {
        TeamMember membership = members.findById(id).orElseThrow(() -> notFound());
        if (!membership.getOwner().getId().equals(userId) && !membership.getMember().getId().equals(userId)) {
            throw notFound();
        }
        members.delete(membership);
    }

    @Transactional(readOnly = true)
    public void requireAccess(Long ownerId, Long userId, boolean editing) {
        if (ownerId.equals(userId)) return;
        // Read the saved role each time. Removed members cannot keep using old access.
        TeamMember membership = members.findByOwnerIdAndMemberIdAndAcceptedTrue(ownerId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found."));
        if (editing && membership.getRole() != TeamMember.Role.EDITOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Viewers cannot change monitors.");
        }
    }

    private Member summary(TeamMember membership) {
        return new Member(membership.getId(), membership.getMember().getEmail(), membership.getRole(), membership.isAccepted());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Team member or invitation not found.");
    }
}
