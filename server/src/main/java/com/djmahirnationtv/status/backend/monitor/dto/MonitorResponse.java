package com.djmahirnationtv.status.backend.monitor.dto;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import java.time.Instant;

public record MonitorResponse(
        Long id,
        String name,
        String url,
        String httpMethod,
        int intervalSeconds,
        int timeoutSeconds,
        MonitorStatus status,
        Instant lastCheckedAt,
        Instant createdAt
) {
    public static MonitorResponse from(Monitor m) {
        return new MonitorResponse(
                m.getId(),
                m.getName(),
                m.getUrl(),
                m.getHttpMethod(),
                m.getIntervalSeconds(),
                m.getTimeoutSeconds(),
                m.getStatus(),
                m.getLastCheckedAt(),
                m.getCreatedAt()
        );
    }
}