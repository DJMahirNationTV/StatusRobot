package com.djmahirnationtv.status.backend.incident;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failed(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
}
