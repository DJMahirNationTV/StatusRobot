package com.djmahirnationtv.status.backend.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/team-members")
public class TeamController {
    private final TeamService teams;

    public TeamController(TeamService teams) { this.teams = teams; }

    public record InviteRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull TeamMember.Role role) {}
    public record RoleRequest(@NotNull TeamMember.Role role) {}

    @GetMapping
    public TeamService.Listing list(Authentication auth) {
        return teams.list(Long.valueOf(auth.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamService.Member invite(@Valid @RequestBody InviteRequest input, Authentication auth) {
        return teams.invite(Long.valueOf(auth.getName()), input.email(), input.role());
    }

    @PostMapping("/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long id, Authentication auth) {
        teams.accept(id, Long.valueOf(auth.getName()));
    }

    @PatchMapping("/{id}")
    public TeamService.Member role(@PathVariable Long id, @Valid @RequestBody RoleRequest input, Authentication auth) {
        return teams.changeRole(id, Long.valueOf(auth.getName()), input.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, Authentication auth) {
        teams.remove(id, Long.valueOf(auth.getName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalid() {
        return Map.of("message", "Enter an email address and choose Viewer or Editor.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict() {
        return Map.of("message", "This team changed. Refresh before trying again.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failed(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }
}
