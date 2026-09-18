package com.djmahirnationtv.status.backend.monitor;

import com.djmahirnationtv.status.backend.monitor.dto.MonitorResponse;
import com.djmahirnationtv.status.backend.monitor.dto.CreateMonitorRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public List<MonitorResponse> listAll() {
        return monitorService.getAllMonitors();
    }

    @GetMapping("/{id}")
    public MonitorResponse getById(@PathVariable Long id) {
        return monitorService.getMonitorById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorResponse create(@Valid @RequestBody CreateMonitorRequest request) {
        return monitorService.createMonitor(request);
    }

    @PatchMapping("/{id}/toggle-pause")
    public MonitorResponse togglePause(@PathVariable Long id) {
        return monitorService.togglePause(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        monitorService.deleteMonitor(id);
    }
}