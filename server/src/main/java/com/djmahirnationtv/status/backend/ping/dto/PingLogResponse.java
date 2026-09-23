package com.djmahirnationtv.status.backend.ping.dto;

import com.djmahirnationtv.status.backend.ping.model.PingLog;
import java.time.Instant;

public record PingLogResponse(
    String id,
    int statusCode,
    long responseTimeMs,
    boolean successful,
    String errorMessage,
    Instant timestamp
) {
    public static PingLogResponse from(PingLog log) {
        return new PingLogResponse(
            log.getId(),
            log.getStatusCode(),
            log.getResponseTimeMs(),
            log.isSuccessful(),
            log.getErrorMessage(),
            log.getTimestamp()
        );
    }
}