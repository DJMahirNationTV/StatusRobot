package com.djmahirnationtv.status.backend.incident;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.team.TeamService;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Collection;

@Service
public class IncidentService {
    private final IncidentRepository incidents;
    private final MonitorRepository monitors;
    private final TeamService teams;

    public IncidentService(IncidentRepository incidents, MonitorRepository monitors, TeamService teams) {
        this.incidents = incidents;
        this.monitors = monitors;
        this.teams = teams;
    }

    @Transactional
    public void recordCheck(Monitor monitor, int statusCode) {
        if (monitor.getOwnerId() == null || monitor.getLastCheckedAt() == null
                || monitor.getStatus() == MonitorStatus.PAUSED) return;

        var open = incidents.findByOpenMonitorId(monitor.getId());
        if (monitor.getStatus() == MonitorStatus.DOWN) {
            if (open.isEmpty()) {
                String cause = statusCode == 0 ? "No HTTP response was received."
                        : "HTTP check returned " + statusCode + ".";
                incidents.save(new Incident(monitor.getOwnerId(), monitor.getId(), monitor.getName(),
                        cause, monitor.getLastCheckedAt()));
            }
        } else {
            open.ifPresent(incident -> incident.resolve(monitor.getLastCheckedAt()));
        }
    }

    public record UpdateResponse(Incident.Stage stage, String message, Instant createdAt) {
        static UpdateResponse from(Incident.Update update) {
            return new UpdateResponse(update.getStage(), update.getMessage(), update.getCreatedAt());
        }
    }

    public record IncidentResponse(Long id, Long monitorId, String monitorName, String cause,
                                   Instant startedAt, Instant resolvedAt, String title, boolean manual,
                                   boolean published, Incident.Stage stage, List<UpdateResponse> updates, Long workspaceId) {
        static IncidentResponse from(Incident incident) {
            return new IncidentResponse(incident.getId(), incident.getMonitorId(), incident.getMonitorName(),
                    incident.getCause(), incident.getStartedAt(), incident.getResolvedAt(), incident.getTitle(),
                    incident.isManual(), incident.isPublished(), incident.getStage(),
                    incident.getUpdates().stream().map(UpdateResponse::from).toList(), incident.getOwnerId());
        }
    }

    public record CreateRequest(@NotNull @Positive Long monitorId, @NotBlank @Size(max = 100) String title,
                                @NotBlank @Size(max = 3000) String message) {}
    public record UpdateRequest(@NotNull Incident.Stage stage, @NotBlank @Size(max = 3000) String message) {}
    public record PublicationRequest(@NotBlank @Size(max = 100) String title, boolean published) {}

    @Transactional
    public IncidentResponse create(CreateRequest input, Long userId) {
        var monitor = monitors.findById(input.monitorId()).filter(m -> m.getOwnerId() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found."));
        teams.requireAccess(monitor.getOwnerId(), userId, true);
        // Shared incidents belong to the monitor's workspace, not the editor.
        return IncidentResponse.from(incidents.saveAndFlush(Incident.manual(monitor.getOwnerId(), monitor.getId(),
                monitor.getName(), input.title().strip(), input.message().strip())));
    }

    @Transactional
    public IncidentResponse update(Long id, UpdateRequest input, Long userId) {
        Incident incident = accessible(id, userId, true);
        if (!incident.isManual() && input.stage() == Incident.Stage.RESOLVED && incident.getResolvedAt() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A successful check must confirm recovery for an automatic incident.");
        if (incident.getResolvedAt() != null && input.stage() != Incident.Stage.RESOLVED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This incident is resolved. Create a new incident instead.");
        if (incident.getUpdates().size() >= 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This incident has reached its limit of 100 updates.");
        incident.addUpdate(input.stage(), input.message().strip());
        return IncidentResponse.from(incidents.saveAndFlush(incident));
    }

    @Transactional
    public IncidentResponse publish(Long id, PublicationRequest input, Long userId) {
        Incident incident = accessible(id, userId, true);
        if (input.published() && incident.getUpdates().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a public update before publishing this incident.");
        incident.publish(input.title().strip(), input.published());
        return IncidentResponse.from(incidents.saveAndFlush(incident));
    }

    private Incident accessible(Long id, Long userId, boolean editing) {
        Incident incident = incidents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found."));
        teams.requireAccess(incident.getOwnerId(), userId, editing);
        return incident;
    }

    public record Listing(List<IncidentResponse> incidents, int page, int totalPages, long totalElements) {}

    public record PublicIncident(Long id, Long monitorId, String title, Incident.Stage stage,
                                 Instant startedAt, Instant resolvedAt, List<UpdateResponse> updates) {}

    @Transactional(readOnly = true)
    public List<PublicIncident> publishedForPage(Long ownerId, Collection<Long> monitorIds) {
        if (monitorIds.isEmpty()) return List.of();
        return incidents.publishedForPage(ownerId, monitorIds, PageRequest.of(0, 40)).stream()
                .map(incident -> new PublicIncident(incident.getId(), incident.getMonitorId(), incident.getTitle(),
                        incident.getStage(), incident.getStartedAt(), incident.getResolvedAt(),
                        incident.getUpdates().stream().map(UpdateResponse::from).toList())).toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id, Long userId) {
        return get(id, userId, false);
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id, Long userId, boolean editing) {
        return IncidentResponse.from(accessible(id, userId, editing));
    }

    @Transactional(readOnly = true)
    public Listing mine(Long userId, String status, int page) {
        return mine(userId, status, page, null);
    }

    @Transactional(readOnly = true)
    public Listing mine(Long userId, String status, int page, Long workspaceId) {
        Long ownerId = teams.workspaceOwner(workspaceId, userId, false);
        if (page < 0 || page > 100_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid page number.");
        }
        var paging = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "startedAt", "id"));
        var result = switch (status) {
            case "all" -> incidents.findByOwnerId(ownerId, paging);
            case "open" -> incidents.findByOwnerIdAndResolvedAtIsNull(ownerId, paging);
            case "resolved" -> incidents.findByOwnerIdAndResolvedAtIsNotNull(ownerId, paging);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose all, open or resolved incidents.");
        };
        return new Listing(result.map(IncidentResponse::from).getContent(), page,
                result.getTotalPages(), result.getTotalElements());
    }
}
