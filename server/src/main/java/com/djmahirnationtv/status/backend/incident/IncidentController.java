package com.djmahirnationtv.status.backend.incident;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentService incidents;

    public IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    public IncidentService.Listing mine(Authentication auth, @RequestParam(defaultValue = "all") String status,
                                        @RequestParam(defaultValue = "0") int page) {
        return incidents.mine(Long.valueOf(auth.getName()), status, page);
    }

    @GetMapping("/{id}")
    public IncidentService.IncidentResponse get(@PathVariable Long id, Authentication auth) {
        return incidents.get(id, Long.valueOf(auth.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentService.IncidentResponse create(@Valid @RequestBody IncidentService.CreateRequest input, Authentication auth) {
        return incidents.create(input, Long.valueOf(auth.getName()));
    }

    @PostMapping("/{id}/updates")
    public IncidentService.IncidentResponse update(@PathVariable Long id, @Valid @RequestBody IncidentService.UpdateRequest input,
                                                   Authentication auth) {
        return incidents.update(id, input, Long.valueOf(auth.getName()));
    }

    @PatchMapping("/{id}")
    public IncidentService.IncidentResponse publish(@PathVariable Long id, @Valid @RequestBody IncidentService.PublicationRequest input,
                                                    Authentication auth) {
        return incidents.publish(id, input, Long.valueOf(auth.getName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("message", "Choose a monitor, title and message within the stated limits."));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(409).body(Map.of("message", "This incident changed. Refresh before trying again."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failed(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
}
