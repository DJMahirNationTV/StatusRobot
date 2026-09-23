package com.djmahirnationtv.status.backend.ping.dto;

public record MonitorStatsResponse(
    Long monitorId,
    double uptimePercentage,
    long averageResponseTimeMs,
    long totalPings,
    long successfulPings
) {}