package com.djmahirnationtv.status.backend.integration;

import com.djmahirnationtv.status.backend.monitor.MonitorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private final IntegrationService integrations;
    private final MonitorService monitors;

    public IntegrationController(IntegrationService integrations, MonitorService monitors) {
        this.integrations = integrations;
        this.monitors = monitors;
    }

    public record Request(@NotBlank @Size(max = 80) String name, @Size(max = 300) String webhookUrl) {}

    @GetMapping
    public IntegrationService.Listing list(Authentication auth) { return integrations.list(Long.valueOf(auth.getName())); }

    @GetMapping("/monitors/{id}")
    public List<Long> selected(@PathVariable Long id, Authentication auth) {
        return monitors.getIntegrationIds(id, Long.valueOf(auth.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationService.Summary create(@Valid @RequestBody Request request, Authentication auth) {
        return integrations.save(null, request.name(), request.webhookUrl(), Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}")
    public IntegrationService.Summary update(@PathVariable Long id, @Valid @RequestBody Request request, Authentication auth) {
        return integrations.save(id, request.name(), request.webhookUrl(), Long.valueOf(auth.getName()));
    }

    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@PathVariable Long id, Authentication auth) { integrations.test(id, Long.valueOf(auth.getName())); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { integrations.delete(id, Long.valueOf(auth.getName())); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid() { return Map.of("message", "Enter a name that is 80 Characters."); }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> failed(ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
}
