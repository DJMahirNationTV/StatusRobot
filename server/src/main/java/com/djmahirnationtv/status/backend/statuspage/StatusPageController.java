package com.djmahirnationtv.status.backend.statuspage;

import jakarta.validation.Valid;
import com.djmahirnationtv.status.backend.statuspage.StatusPageService.StatusPageRequest;
import com.djmahirnationtv.status.backend.statuspage.StatusPageService.StatusPageResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/status-pages")
public class StatusPageController {
    private final StatusPageService pages;
    public StatusPageController(StatusPageService pages) { this.pages = pages; }

    @GetMapping("/mine")
    public StatusPageService.Listing mine(Authentication auth) { return pages.mine(Long.valueOf(auth.getName())); }

    @GetMapping("/default")
    public ResponseEntity<StatusPageResponse> defaultPage() {
        var page = pages.defaultPage();
        return page == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(page);
    }

    @GetMapping("/{slug}")
    public StatusPageResponse publicPage(@PathVariable String slug) { return pages.bySlug(slug); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StatusPageResponse create(@Valid @RequestBody StatusPageRequest input, Authentication auth) {
        return pages.save(null, input, Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}")
    public StatusPageResponse update(@PathVariable Long id, @Valid @RequestBody StatusPageRequest input, Authentication auth) {
        return pages.save(id, input, Long.valueOf(auth.getName()));
    }

    public record PinRequest(boolean pinnedDefault) {}
    @PatchMapping("/{id}/pin-default")
    public StatusPageResponse pin(@PathVariable Long id, @RequestBody PinRequest input, Authentication auth) {
        return pages.pin(id, input.pinnedDefault(), Long.valueOf(auth.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { pages.delete(id, Long.valueOf(auth.getName())); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid() { return Map.of("message", "Enter a name, a lowercase page address, and up to 30 of your monitors."); }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict() { return Map.of("message", "The page changed or its address is already in use. Refresh and try again."); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failed(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
}
