package com.djmahirnationtv.status.backend.maintenance;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "maintenance_windows", indexes = {
        @Index(name = "maintenance_owner_schedule", columnList = "owner_id,starts_at,id")
})
public class Maintenance {
    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Prevent two requests from saving different changes over each other.
    @Version
    private long version;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 3000)
    private String description = "";

    @Column(name = "starts_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant startsAt;

    @Column(nullable = false, columnDefinition = "DATETIME(6)")
    private Instant endsAt;

    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private boolean cancelled;

    @Column(nullable = false)
    private boolean published;

    @ManyToMany
    @JoinTable(name = "maintenance_monitors", joinColumns = @JoinColumn(name = "maintenance_id"),
            inverseJoinColumns = @JoinColumn(name = "monitor_id"))
    private Set<Monitor> monitors = new HashSet<>();

    protected Maintenance() {}

    public Maintenance(Long ownerId) { this.ownerId = ownerId; }

    // Work out the status from the dates. No extra job needs to update it.
    public Status statusAt(Instant now) {
        if (cancelled) return Status.CANCELLED;
        if (now.isBefore(startsAt)) return Status.SCHEDULED;
        if (!now.isBefore(endsAt)) return Status.COMPLETED;
        return Status.IN_PROGRESS;
    }
}
