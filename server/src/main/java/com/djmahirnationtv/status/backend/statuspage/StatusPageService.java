package com.djmahirnationtv.status.backend.statuspage;

import com.djmahirnationtv.status.backend.auth.AppUser;
import com.djmahirnationtv.status.backend.auth.UserRepository;
import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        List<StatusPageResponse> result = new ArrayList<>();
        for (StatusPage page : pages.findByOwnerIdOrderByIdDesc(userId)) {
            result.add(StatusPageResponse.from(page));
        }
        return new Listing(canPin(userId, serverOwner()), List.copyOf(result));
    }

    @Transactional(readOnly = true)
    public StatusPageResponse bySlug(String slug) {
        StatusPage page = pages.findBySlug(slug).orElse(null);
        if (page == null) throw notFound();
        return publicView(page);
    }

    @Transactional(readOnly = true)
    public StatusPageResponse defaultPage() {
        StatusPage page = pages.findByDefaultSlot(1).orElse(null);
        return page == null ? null : publicView(page);
    }

    private StatusPageResponse publicView(StatusPage page) {
        var response = StatusPageResponse.from(page);
        List<Long> monitorIds = new ArrayList<>();
        for (MonitorResponse monitor : response.monitors()) {
            monitorIds.add(monitor.id());
        }
        // C.DE: Öffentlich erscheinen nur veröffentlichte Meldungen für die ausgewählten Monitore dieses Workspaces.
        return new StatusPageResponse(response.id(), response.name(), response.slug(), response.description(),
                response.pinnedDefault(), response.monitors(), incidents.publishedForPage(page.getOwnerId(), monitorIds),
                maintenance.publishedForPage(page.getOwnerId(), monitorIds));
    }

    @Transactional
    public StatusPageResponse save(Long id, StatusPageRequest input, Long userId) {
        AppUser owner = lockSettings();
        StatusPage page = id == null ? new StatusPage() : owned(id, userId);
        long excludedId = id == null ? -1L : id;
        if (input.slug().equals("mine") || input.slug().equals("default")
                || pages.existsBySlugAndIdNot(input.slug(), excludedId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This page address is already in use. Choose another one.");
        }
        List<Monitor> selected = selectedMonitors(input.monitorIds(), userId);
        page.setOwnerId(userId);
        page.setName(input.name().strip());
        page.setSlug(input.slug());
        page.setDescription(input.description() == null ? "" : input.description().strip());
        page.getMonitors().clear();
        page.getMonitors().addAll(selected);
        setDefault(page, input.pinnedDefault(), userId, owner);
        return StatusPageResponse.from(pages.saveAndFlush(page));
    }

    private List<Monitor> selectedMonitors(List<Long> ids, Long userId) {
        List<Monitor> selected = monitors.findAllById(ids);
        // C.DE: Doppelte IDs zählen nur einmal. Fehlende oder fremde Monitore dürfen nicht auf die Seite.
        if (selected.size() != new HashSet<>(ids).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select monitors from your own account.");
        }
        for (Monitor monitor : selected) {
            if (!userId.equals(monitor.getOwnerId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select monitors from your own account.");
            }
        }
        return selected;
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
        if ((pinned || page.isPinnedDefault()) && !canPin(userId, owner)) {
            denyPin();
        }
        if (pinned) {
            StatusPage old = pages.findByDefaultSlot(1).orElse(null);
            if (old != null && !old.getId().equals(page.getId())) {
                old.pinDefault(false);
                // C.DE: Erst wird der alte Default-Platz freigegeben, danach kann die neue Seite ihn übernehmen.
                pages.flush();
            }
        }
        page.pinDefault(pinned);
    }

    private AppUser serverOwner() {
        if (ownerEmail.isEmpty()) {
            return users.findFirstByOrderByIdAsc().orElse(null);
        }
        return users.findByEmail(ownerEmail).orElse(null);
    }

    private AppUser lockSettings() {
        AppUser owner = serverOwner();
        if (owner == null) return null;
        // C.DE: Die Datenbanksperre verhindert, dass zwei gleichzeitige Änderungen mehrere Default-Seiten erzeugen.
        return users.lockForSettingsUpdate(owner.getId()).orElseThrow();
    }

    private boolean canPin(Long userId, AppUser owner) { return owner != null && owner.getId().equals(userId); }
    private void denyPin() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the server owner can change the default page."); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Status page not found"); }
    private StatusPage owned(Long id, Long userId) {
        StatusPage page = pages.findById(id).orElse(null);
        if (page == null || !userId.equals(page.getOwnerId())) throw notFound();
        return page;
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
            List<MonitorResponse> monitors = new ArrayList<>();
            for (Monitor monitor : page.getMonitors()) {
                if (page.getOwnerId().equals(monitor.getOwnerId())) {
                    monitors.add(MonitorResponse.from(monitor));
                }
            }
            // C.DE: Der DTO enthält nur eigene Monitore und sortiert sie wie bisher nach Namen.
            monitors.sort(Comparator.comparing(MonitorResponse::name));
            return new StatusPageResponse(page.getId(), page.getName(), page.getSlug(), page.getDescription(),
                    page.isPinnedDefault(), List.copyOf(monitors), List.of(), List.of());
        }
    }
}
