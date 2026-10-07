package com.djmahirnationtv.status.backend.incident;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@Service
public class IncidentService {
    private final IncidentRepository incidents;

    public IncidentService(IncidentRepository incidents) {
        this.incidents = incidents;
    }

    public record IncidentResponse(Long id, Long monitorId, String monitorName, String cause,
                                   Instant startedAt, Instant resolvedAt) {
        static IncidentResponse from(Incident incident) {
            return new IncidentResponse(incident.getId(), incident.getMonitorId(), incident.getMonitorName(),
                    incident.getCause(), incident.getStartedAt(), incident.getResolvedAt());
        }
    }

    public record Listing(List<IncidentResponse> incidents, int page, int totalPages, long totalElements) {}

    @Transactional(readOnly = true)
    public Listing mine(Long ownerId, String status, int page) {
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
