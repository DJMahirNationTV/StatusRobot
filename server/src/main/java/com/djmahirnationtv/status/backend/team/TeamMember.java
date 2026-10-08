package com.djmahirnationtv.status.backend.team;

import com.djmahirnationtv.status.backend.auth.AppUser;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "team_members", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "member_id"}))
public class TeamMember {
    public enum Role { VIEWER, EDITOR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private AppUser owner;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private AppUser member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(nullable = false)
    private boolean accepted;

    protected TeamMember() {}

    public TeamMember(AppUser owner, AppUser member, Role role) {
        this.owner = owner;
        this.member = member;
        this.role = role;
    }

    public void accept() { accepted = true; }
    public void setRole(Role role) { this.role = role; }
}
