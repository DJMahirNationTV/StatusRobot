package com.djmahirnationtv.status.backend.incident;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean manual;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean published;
    @Column(length = 100)
    private String title;
    @ElementCollection
    @CollectionTable(name = "incident_updates", joinColumns = @JoinColumn(name = "incident_id"))
    @OrderColumn(name = "update_position")
    private List<Update> updates = new ArrayList<>();

    public enum Stage { INVESTIGATING, IDENTIFIED, MONITORING, RESOLVED }

    @Getter
    @Embeddable
    public static class Update {
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private Stage stage;
        @Column(nullable = false, length = 3000)
        private String message;
        @Column(nullable = false, columnDefinition = "DATETIME(6)")
        private Instant createdAt;

        protected Update() {}

        public Update(Stage stage, String message, Instant createdAt) {
            this.stage = stage;
            this.message = message;
            this.createdAt = createdAt;
        }
    }

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

    public static Incident manual(Long ownerId, Long monitorId, String monitorName, String title, String message) {
        var incident = new Incident(ownerId, monitorId, monitorName, "Reported by the monitor owner.", Instant.now());
        incident.manual = true;
        incident.openMonitorId = null;
        incident.title = title;
        incident.addUpdate(Stage.INVESTIGATING, message);
        return incident;
    }

    public String getTitle() { return title == null ? monitorName + " unavailable" : title; }

    public Stage getStage() {
        if (resolvedAt != null) return Stage.RESOLVED;
        return updates.isEmpty() ? Stage.INVESTIGATING : updates.getLast().getStage();
    }

    public void addUpdate(Stage stage, String message) {
        Instant now = Instant.now();
        updates.add(new Update(stage, message, now));
        if (stage == Stage.RESOLVED) resolve(now);
    }

    public void publish(String title, boolean published) {
        this.title = title;
        this.published = published;
    }
}
