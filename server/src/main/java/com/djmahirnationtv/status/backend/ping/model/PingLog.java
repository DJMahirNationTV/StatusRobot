package com.djmahirnationtv.status.backend.ping.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Document(collection = "ping_logs")
public class PingLog {
    @Id
    private String id;
    @Indexed
    private Long monitorId;
    private int statusCode;
    private long responseTimeMs;
    private boolean successful;
    private String errorMessage;

    @Indexed
    private Instant timestamp = Instant.now();

    public PingLog() {
    }

    public PingLog(Long monitorId, int statusCode, long responseTimeMs, boolean successful, String errorMessage) {
        this.monitorId = monitorId;
        this.statusCode = statusCode;
        this.responseTimeMs = responseTimeMs;
        this.successful = successful;
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }
    public Long getMonitorId() {
        return monitorId;
    }
    public int getStatusCode() {
        return statusCode;
    }
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
    public boolean isSuccessful() {
        return successful;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    public Instant getTimestamp() {
        return timestamp;
    }
}