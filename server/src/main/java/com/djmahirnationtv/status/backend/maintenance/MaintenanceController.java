package com.djmahirnationtv.status.backend.maintenance;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {
    private final MaintenanceService maintenance;

    public MaintenanceController(MaintenanceService maintenance) { this.maintenance = maintenance; }

    @GetMapping("/mine")
    public MaintenanceService.Listing mine(Authentication auth, @RequestParam(defaultValue = "0") int page) {
        return maintenance.mine(Long.valueOf(auth.getName()), page);
    }

    @GetMapping("/{id}")
    public MaintenanceService.Response get(@PathVariable Long id, Authentication auth) {
        return maintenance.get(id, Long.valueOf(auth.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceService.Response create(@Valid @RequestBody MaintenanceService.SaveRequest input, Authentication auth) {
        return maintenance.save(null, input, Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}")
    public MaintenanceService.Response update(@PathVariable Long id, @Valid @RequestBody MaintenanceService.SaveRequest input, Authentication auth) {
        return maintenance.save(id, input, Long.valueOf(auth.getName()));
    }

    @PatchMapping("/{id}/cancel")
    public MaintenanceService.Response cancel(@PathVariable Long id, Authentication auth) {
        return maintenance.cancel(id, Long.valueOf(auth.getName()));
    }

    @PatchMapping("/{id}/publication")
    public MaintenanceService.Response publish(@PathVariable Long id, @Valid @RequestBody MaintenanceService.PublicationRequest input, Authentication auth) {
        return maintenance.publish(id, input, Long.valueOf(auth.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { maintenance.delete(id, Long.valueOf(auth.getName())); }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid() {
        return Map.of("message", "Enter a title, valid start and end times, and between 1 and 30 monitor IDs.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict() {
        return Map.of("message", "Maintenance or its monitors changed. Refresh and try again.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failed(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
}
