package com.djmahirnationtv.status.backend.monitor;

import com.djmahirnationtv.status.backend.monitor.dto.MonitorResponse;
import com.djmahirnationtv.status.backend.monitor.dto.CreateMonitorRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
    public MonitorResponse create(@Valid @RequestBody CreateMonitorRequest request, Authentication authentication) {
        return monitorService.createMonitor(request, Long.valueOf(authentication.getName()));
    }

    @PatchMapping("/{id}/toggle-pause")
    public MonitorResponse togglePause(@PathVariable Long id, Authentication authentication) {
        return monitorService.togglePause(id, Long.valueOf(authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        monitorService.deleteMonitor(id, Long.valueOf(authentication.getName()));
    }
}
