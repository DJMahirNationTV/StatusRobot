package com.djmahirnationtv.status.backend.monitor;

import com.djmahirnationtv.status.backend.monitor.dto.MonitorResponse;
import com.djmahirnationtv.status.backend.monitor.dto.MonitorRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.Map;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import java.util.List;
import com.djmahirnationtv.status.backend.integration.IntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

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

    @GetMapping("/mine")
    public List<MonitorResponse> listMine(Authentication authentication, @RequestParam(required = false) Long workspaceId) {
        Long userId = Long.valueOf(authentication.getName());
        return monitorService.getWorkspaceMonitors(workspaceId == null ? userId : workspaceId, userId);
    }

    @GetMapping("/workspace/integrations")
    public IntegrationService.Listing workspaceIntegrations(Authentication authentication,
                                                           @RequestParam(required = false) Long workspaceId) {
        Long userId = Long.valueOf(authentication.getName());
        return monitorService.getWorkspaceIntegrations(workspaceId == null ? userId : workspaceId, userId);
    }

    @GetMapping("/{id}")
    public MonitorResponse getById(@PathVariable Long id) {
        return monitorService.getMonitorById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonitorResponse create(@Valid @RequestBody MonitorRequest request, Authentication authentication,
                                  @RequestParam(required = false) Long workspaceId) {
        Long userId = Long.valueOf(authentication.getName());
        return monitorService.createMonitor(request, workspaceId == null ? userId : workspaceId, userId);
    }

    @PutMapping("/{id}")
    public MonitorResponse update(@PathVariable Long id, @Valid @RequestBody MonitorRequest request, Authentication authentication) {
        return monitorService.updateMonitor(id, request, Long.valueOf(authentication.getName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidMonitor(MethodArgumentNotValidException exception) {
        return Map.of("message", exception.getBindingResult().getAllErrors().getFirst().getDefaultMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> concurrentChange() {
        return Map.of("message", "This monitor changed during your request. Please try again.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failed(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
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
