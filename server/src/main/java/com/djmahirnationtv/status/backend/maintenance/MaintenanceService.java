package com.djmahirnationtv.status.backend.maintenance;

import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class MaintenanceService {
    private final MaintenanceRepository windows;
    private final MonitorRepository monitors;

    public MaintenanceService(MaintenanceRepository windows, MonitorRepository monitors) {
        this.windows = windows;
        this.monitors = monitors;
    }

    public record SaveRequest(@NotBlank @Size(max = 100) String title, @Size(max = 3000) String description,
                              @NotNull Instant startsAt, @NotNull Instant endsAt,
                              @NotNull @Size(min = 1, max = 30) List<@NotNull @Positive Long> monitorIds,
                              boolean published) {}
    public record PublicationRequest(@NotNull Boolean published) {}
    public record Response(Long id, String title, String description, Instant startsAt, Instant endsAt,
                           Maintenance.Status status, boolean published, List<Long> monitorIds, Instant createdAt) {}
    public record Listing(List<Response> maintenance, int page, int totalPages, long totalElements) {}
    public record PublicNotice(Long id, String title, String description, Instant startsAt, Instant endsAt,
                               Maintenance.Status status, List<Long> monitorIds) {}

    @Transactional(readOnly = true)
    public Listing mine(Long ownerId, int page) {
        if (page < 0 || page > 100_000) throw badRequest("Choose a valid page number.");
        var paging = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "startsAt", "id"));
        var result = windows.findByOwnerId(ownerId, paging);
        Instant now = Instant.now();
        return new Listing(result.map(window -> response(window, now)).getContent(), page,
                result.getTotalPages(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Response get(Long id, Long ownerId) { return response(owned(id, ownerId), Instant.now()); }

    @Transactional
    public Response save(Long id, SaveRequest input, Long ownerId) {
        Instant now = Instant.now();
        Maintenance window = id == null ? new Maintenance(ownerId) : owned(id, ownerId);
        if (id != null && window.statusAt(now) != Maintenance.Status.SCHEDULED) {
            throw conflict("Only upcoming maintenance can be edited. Cancel ongoing maintenance instead.");
        }
        if (!input.startsAt().isAfter(now)) throw badRequest("Choose a start time in the future.");
        if (!input.endsAt().isAfter(input.startsAt())) throw badRequest("The end time must be after the start time.");

        var selected = monitors.findAllById(input.monitorIds());
        if (selected.size() != input.monitorIds().size()) throw badRequest("Select between 1 and 30 different monitors from your account.");
        for (var monitor : selected) {
            if (!ownerId.equals(monitor.getOwnerId())) throw badRequest("Select monitors from your own account.");
        }
        window.setTitle(input.title().strip());
        window.setDescription(input.description() == null ? "" : input.description().strip());
        window.setStartsAt(input.startsAt());
        window.setEndsAt(input.endsAt());
        window.setPublished(input.published());
        window.getMonitors().clear();
        window.getMonitors().addAll(selected);
        return response(windows.saveAndFlush(window), now);
    }

    @Transactional
    public Response cancel(Long id, Long ownerId) {
        Maintenance window = owned(id, ownerId);
        Instant now = Instant.now();
        if (window.statusAt(now) == Maintenance.Status.COMPLETED) throw conflict("Completed maintenance cannot be cancelled.");
        window.setCancelled(true);
        return response(windows.saveAndFlush(window), now);
    }

    @Transactional
    public Response publish(Long id, PublicationRequest input, Long ownerId) {
        Maintenance window = owned(id, ownerId);
        window.setPublished(input.published());
        return response(windows.saveAndFlush(window), Instant.now());
    }

    @Transactional
    public void delete(Long id, Long ownerId) {
        Maintenance window = owned(id, ownerId);
        if (window.statusAt(Instant.now()) == Maintenance.Status.IN_PROGRESS) {
            throw conflict("Cancel ongoing maintenance before deleting it.");
        }
        windows.delete(window);
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long monitorId, Instant now) { return windows.isActive(monitorId, now); }

    @Transactional(readOnly = true)
    public List<PublicNotice> publishedForPage(Long ownerId, Collection<Long> monitorIds) {
        if (monitorIds.isEmpty()) return List.of();
        Instant now = Instant.now();
        List<PublicNotice> notices = new ArrayList<>();
        for (Maintenance window : windows.publishedForPage(ownerId, monitorIds, now, PageRequest.of(0, 40))) {
            List<Long> selectedIds = new ArrayList<>();
            for (var monitor : window.getMonitors()) {
                if (monitorIds.contains(monitor.getId()) && ownerId.equals(monitor.getOwnerId())) {
                    selectedIds.add(monitor.getId());
                }
            }
            selectedIds.sort(Long::compareTo);
            notices.add(new PublicNotice(window.getId(), window.getTitle(), window.getDescription(),
                    window.getStartsAt(), window.getEndsAt(), window.statusAt(now), selectedIds));
        }
        return notices;
    }

    @Transactional
    public void removeMonitor(Long monitorId) {
        // Remove the links first, otherwise the database cannot delete the monitor.
        Instant now = Instant.now();
        for (Maintenance window : windows.findByMonitorsId(monitorId)) {
            window.getMonitors().removeIf(monitor -> monitor.getId().equals(monitorId));
            if (window.getMonitors().isEmpty() && window.statusAt(now) != Maintenance.Status.COMPLETED) {
                window.setCancelled(true);
            }
        }
        windows.flush();
    }

    private Maintenance owned(Long id, Long ownerId) {
        return windows.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance not found."));
    }

    private Response response(Maintenance window, Instant now) {
        return new Response(window.getId(), window.getTitle(), window.getDescription(), window.getStartsAt(),
                window.getEndsAt(), window.statusAt(now), window.isPublished(),
                window.getMonitors().stream().map(monitor -> monitor.getId()).sorted().toList(), window.getCreatedAt());
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
