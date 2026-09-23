package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.dto.MonitorStatsResponse;
import com.djmahirnationtv.status.backend.ping.dto.PingLogResponse;
import com.djmahirnationtv.status.backend.ping.model.PingLog;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class PingService {

    private final PingLogRepository pingLogRepository;
    private final MonitorRepository monitorRepository;

    public PingService(PingLogRepository pingLogRepository, MonitorRepository monitorRepository) {
        this.pingLogRepository = pingLogRepository;
        this.monitorRepository = monitorRepository;
    }

    public List<PingLogResponse> getRecentPings(Long monitorId, int limit) {
        validateMonitorExists(monitorId);

        int maxLimit = Math.min(Math.max(limit, 1), 100);
        return pingLogRepository.findByMonitorIdOrderByTimestampDesc(monitorId, PageRequest.of(0, maxLimit))
                .stream()
                .map(PingLogResponse::from)
                .toList();
    }

    public MonitorStatsResponse get24HourStats(Long monitorId) {
        validateMonitorExists(monitorId);

        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        List<PingLog> logs = pingLogRepository.findByMonitorIdAndTimestampAfterOrderByTimestampAsc(
                monitorId, twentyFourHoursAgo
        );

        if (logs.isEmpty()) {
            return new MonitorStatsResponse(monitorId, 100.0, 0, 0, 0);
        }

        long total = logs.size();
        long successful = logs.stream().filter(PingLog::isSuccessful).count();
        long totalResponseTime = logs.stream().mapToLong(PingLog::getResponseTimeMs).sum();

        double uptimePercentage = Math.round(((double) successful / total * 100.0) * 100.0) / 100.0;
        long averageResponseTime = totalResponseTime / total;

        return new MonitorStatsResponse(monitorId, uptimePercentage, averageResponseTime, total, successful);
    }

    private void validateMonitorExists(Long monitorId) {
        if (!monitorRepository.existsById(monitorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found");
        }
    }
}