package com.djmahirnationtv.status.backend.monitor.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "monitors")
public class Monitor {

    // the Getters and Setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String httpMethod = "GET";

    @Column(nullable = false)
    private int intervalSeconds = 60;

    @Column(nullable = false)
    private int timeoutSeconds = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MonitorStatus status = MonitorStatus.PAUSED;

    @Column(columnDefinition = "DATETIME")
    private Instant lastCheckedAt;

    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME")
    private Instant createdAt = Instant.now();

    public Monitor() {
    }

    public Monitor(String name, String url, String httpMethod, int intervalSeconds, int timeoutSeconds) {
        this.name = name;
        this.url = url;
        this.httpMethod = (httpMethod != null && !httpMethod.isBlank()) ? httpMethod.toUpperCase() : "GET";
        this.intervalSeconds = intervalSeconds > 0 ? intervalSeconds : 60;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 5;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setStatus(MonitorStatus status) {
        this.status = status;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

}