package com.djmahirnationtv.status.backend.incident;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Getter
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "incident_owner_history", columnList = "owner_id,started_at,id")
})
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private long version;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;
    @Column(nullable = false, length = 100)
    private String monitorName;
    @Column(nullable = false, length = 200)
    private String cause;
    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant startedAt;
    @Column(columnDefinition = "DATETIME(6)")
    private Instant resolvedAt;
    // A unique monitor ID while open, null after recovery. Closed incidents can share a monitor.
    @Column(unique = true)
    private Long openMonitorId;

    protected Incident() {}

    public Incident(Long ownerId, Long monitorId, String monitorName, String cause, Instant startedAt) {
        this.ownerId = ownerId;
        this.monitorId = monitorId;
        this.monitorName = monitorName;
        this.cause = cause;
        this.startedAt = startedAt;
        this.openMonitorId = monitorId;
    }

    public void resolve(Instant checkedAt) {
        if (resolvedAt == null) {
            resolvedAt = checkedAt;
            openMonitorId = null;
        }
    }
}
