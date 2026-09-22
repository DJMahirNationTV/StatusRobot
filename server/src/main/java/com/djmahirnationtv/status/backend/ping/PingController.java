package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.ping.dto.MonitorStatsResponse;
import com.djmahirnationtv.status.backend.ping.dto.PingLogResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/monitors/{monitorId}")
public class PingController {

    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
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