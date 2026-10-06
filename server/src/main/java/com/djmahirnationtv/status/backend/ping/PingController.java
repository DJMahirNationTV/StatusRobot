package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.ping.dto.MonitorStatsResponse;
import com.djmahirnationtv.status.backend.ping.dto.PingLogResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/monitors/{monitorId}")
public class PingController {

    private final PingService pingService;
    private final PingHistoryService history;

    public PingController(PingService pingService, PingHistoryService history) {
        this.pingService = pingService;
        this.history = history;
    }

    @GetMapping("/history")
    public PingHistoryService.MonitorHistoryResponse history(
            @PathVariable Long monitorId, @RequestParam(defaultValue = "24") int hours) {
        return history.history(monitorId, hours);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
    public java.util.Map<String, String> unavailable() {
        return java.util.Map.of("message", "Monitoring history is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/pings")
    public List<PingLogResponse> getRecentPings(
            @PathVariable Long monitorId,
            @RequestParam(defaultValue = "30") int limit) {
        return pingService.getRecentPings(monitorId, limit);
    }

    @GetMapping("/stats")
    public MonitorStatsResponse get24HourStats(@PathVariable Long monitorId) {
        return pingService.get24HourStats(monitorId);
    }
}
