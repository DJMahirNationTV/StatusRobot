package com.djmahirnationtv.status.backend.statuspage;

import com.djmahirnationtv.status.backend.auth.AppUser;
import com.djmahirnationtv.status.backend.auth.UserRepository;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import jakarta.validation.constraints.*;
import com.djmahirnationtv.status.backend.monitor.dto.MonitorResponse;
import com.djmahirnationtv.status.backend.incident.IncidentService;
import com.djmahirnationtv.status.backend.maintenance.MaintenanceService;

@Service
public class StatusPageService {
    private final StatusPageRepository pages;
    private final MonitorRepository monitors;
    private final UserRepository users;
    private final String ownerEmail;
    private final IncidentService incidents;
    private final MaintenanceService maintenance;

    public StatusPageService(StatusPageRepository pages, MonitorRepository monitors, UserRepository users,
                             @Value("${app.status-pages.owner-email:}") String ownerEmail, IncidentService incidents, MaintenanceService maintenance) {
        this.pages = pages;
        this.monitors = monitors;
        this.users = users;
        this.ownerEmail = ownerEmail.strip().toLowerCase(java.util.Locale.ROOT);
        this.incidents = incidents;
        this.maintenance = maintenance;
    }

    public record Listing(boolean canPinDefault, List<StatusPageResponse> pages) {}

    @Transactional(readOnly = true)
    public Listing mine(Long userId) {
        return new Listing(canPin(userId, serverOwner()), pages.findByOwnerIdOrderByIdDesc(userId).stream()
                .map(StatusPageResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public StatusPageResponse bySlug(String slug) {
        return publicView(pages.findBySlug(slug).orElseThrow(() -> notFound()));
    }

    @Transactional(readOnly = true)
    public StatusPageResponse defaultPage() {
        return pages.findByDefaultSlot(1).map(this::publicView).orElse(null);
    }

    private StatusPageResponse publicView(StatusPage page) {
        var response = StatusPageResponse.from(page);
        var monitorIds = response.monitors().stream().map(MonitorResponse::id).toList();
        return new StatusPageResponse(response.id(), response.name(), response.slug(), response.description(),
                response.pinnedDefault(), response.monitors(), incidents.publishedForPage(page.getOwnerId(), monitorIds),
                maintenance.publishedForPage(page.getOwnerId(), monitorIds));
    }

    @Transactional
    public StatusPageResponse save(Long id, StatusPageRequest input, Long userId) {
        AppUser owner = lockSettings();
        StatusPage page = id == null ? new StatusPage() : owned(id, userId);
        if (Set.of("mine", "default").contains(input.slug()) || pages.existsBySlugAndIdNot(input.slug(), id == null ? -1L : id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This page address is already in use. Choose another one.");
        var selected = monitors.findAllById(input.monitorIds());
        if (selected.size() != input.monitorIds().stream().distinct().count()
                || selected.stream().anyMatch(m -> !userId.equals(m.getOwnerId())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select monitors from your own account.");
        page.setOwnerId(userId);
        page.setName(input.name().strip());
        page.setSlug(input.slug());
        page.setDescription(input.description() == null ? "" : input.description().strip());
        page.getMonitors().clear();
        page.getMonitors().addAll(selected);
        setDefault(page, input.pinnedDefault(), userId, owner);
        return StatusPageResponse.from(pages.saveAndFlush(page));
    }

    @Transactional
    public StatusPageResponse pin(Long id, boolean pinned, Long userId) {
        AppUser owner = lockSettings();
        StatusPage page = owned(id, userId);
        setDefault(page, pinned, userId, owner);
        return StatusPageResponse.from(pages.saveAndFlush(page));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        AppUser owner = lockSettings();
        StatusPage page = owned(id, userId);
        if (page.isPinnedDefault() && !canPin(userId, owner)) denyPin();
        pages.delete(page);
    }

    private void setDefault(StatusPage page, boolean pinned, Long userId, AppUser owner) {
        if (pinned || page.isPinnedDefault() != pinned) {
            if (!canPin(userId, owner)) denyPin();
        }
        if (pinned) {
            pages.findByDefaultSlot(1).filter(old -> !old.getId().equals(page.getId())).ifPresent(old -> {
                old.pinDefault(false);
                pages.flush();
            });
        }
        page.pinDefault(pinned);
    }

    private AppUser serverOwner() {
        return (ownerEmail.isEmpty() ? users.findFirstByOrderByIdAsc() : users.findByEmail(ownerEmail)).orElse(null);
    }

    private AppUser lockSettings() {
        AppUser owner = serverOwner();
        return owner == null ? null : users.lockForSettingsUpdate(owner.getId()).orElseThrow();
    }

    private boolean canPin(Long userId, AppUser owner) { return owner != null && owner.getId().equals(userId); }
    private void denyPin() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the server owner can change the default page."); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Status page not found"); }
    private StatusPage owned(Long id, Long userId) {
        return pages.findById(id).filter(page -> userId.equals(page.getOwnerId())).orElseThrow(() -> notFound());
    }

    public record StatusPageRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @Size(max = 500) String description,
            @NotNull @Size(max = 30) List<@NotNull @Positive Long> monitorIds,
            boolean pinnedDefault) {}

    public record StatusPageResponse(Long id, String name, String slug, String description,
                                     boolean pinnedDefault, List<MonitorResponse> monitors,
                                     List<IncidentService.PublicIncident> incidents, List<MaintenanceService.PublicNotice> maintenance) {
        static StatusPageResponse from(StatusPage page) {
            var monitors = page.getMonitors().stream().filter(m -> page.getOwnerId().equals(m.getOwnerId()))
                    .map(MonitorResponse::from).sorted(Comparator.comparing(MonitorResponse::name)).toList();
            return new StatusPageResponse(page.getId(), page.getName(), page.getSlug(), page.getDescription(),
                    page.isPinnedDefault(), monitors, List.of(), List.of());
        }
    }
}
