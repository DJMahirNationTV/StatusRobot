package com.djmahirnationtv.status.backend.monitor;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.dto.MonitorResponse;
import com.djmahirnationtv.status.backend.monitor.dto.MonitorRequest;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.util.List;
import com.djmahirnationtv.status.backend.integration.IntegrationService;
import com.djmahirnationtv.status.backend.statuspage.StatusPageRepository;
import com.djmahirnationtv.status.backend.incident.IncidentRepository;
import com.djmahirnationtv.status.backend.team.TeamService;
import com.djmahirnationtv.status.backend.maintenance.MaintenanceService;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final PingLogRepository pingLogRepository;
    private final IntegrationService integrations;
    private final StatusPageRepository statusPages;
    private final IncidentRepository incidents;
    private final TeamService teams;
    private final MaintenanceService maintenance;

    public MonitorService(MonitorRepository monitorRepository, PingLogRepository pingLogRepository, IntegrationService integrations,
                          StatusPageRepository statusPages, IncidentRepository incidents, TeamService teams, MaintenanceService maintenance) {
        this.monitorRepository = monitorRepository;
        this.pingLogRepository = pingLogRepository;
        this.integrations = integrations;
        this.statusPages = statusPages;
        this.incidents = incidents;
        this.teams = teams;
        this.maintenance = maintenance;
    }

    public List<MonitorResponse> getAllMonitors() {
        return monitorRepository.findAll().stream()
                .map(MonitorResponse::from)
                .toList();
    }

    public MonitorResponse getMonitorById(Long id) {
        return monitorRepository.findById(id)
                .map(MonitorResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found"));
    }

    @Transactional
    public MonitorResponse createMonitor(MonitorRequest req, Long ownerId) {
        return createMonitor(req, ownerId, ownerId);
    }

    @Transactional
    public MonitorResponse createMonitor(MonitorRequest req, Long ownerId, Long userId) {
        teams.requireAccess(ownerId, userId, true);
        Monitor monitor = new Monitor(
            req.name().strip(),
            req.url().strip(),
            req.httpMethod(),
            req.intervalSeconds(),
            req.timeoutSeconds()
        );
        monitor.setOwnerId(ownerId);
        monitor.getIntegrations().addAll(integrations.selection(req.integrationIds(), ownerId));
        monitor.setStatus(MonitorStatus.UP);
        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    public List<MonitorResponse> getOwnedMonitors(Long ownerId) {
        return monitorRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(MonitorResponse::from).toList();
    }

    public List<MonitorResponse> getWorkspaceMonitors(Long ownerId, Long userId) {
        teams.requireAccess(ownerId, userId, false);
        return getOwnedMonitors(ownerId);
    }

    public IntegrationService.Listing getWorkspaceIntegrations(Long ownerId, Long userId) {
        teams.requireAccess(ownerId, userId, true);
        return integrations.list(ownerId);
    }

    @Transactional
    public MonitorResponse updateMonitor(Long id, MonitorRequest req, Long userId) {
        Monitor monitor = editableMonitor(id, userId);
        boolean targetChanged = !monitor.getUrl().equals(req.url()) || !monitor.getHttpMethod().equals(req.httpMethod());
        monitor.setName(req.name().strip());
        monitor.setUrl(req.url().strip());
        monitor.setHttpMethod(req.httpMethod());
        monitor.setIntervalSeconds(req.intervalSeconds());
        monitor.setTimeoutSeconds(req.timeoutSeconds());
        if (req.integrationIds() != null) {
            // Integrations belong to the monitor owner, not the teammate editing it.
            var selected = integrations.selection(req.integrationIds(), monitor.getOwnerId());
            monitor.getIntegrations().clear();
            monitor.getIntegrations().addAll(selected);
        }
        if (targetChanged) monitor.setLastCheckedAt(null);
        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public MonitorResponse togglePause(Long id, Long userId) {
        Monitor monitor = editableMonitor(id, userId);

        if (monitor.getStatus() == MonitorStatus.PAUSED) {
            monitor.setStatus(MonitorStatus.UP);
            monitor.setLastCheckedAt(null);
        } else {
            monitor.setStatus(MonitorStatus.PAUSED);
        }

        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public void deleteMonitor(Long id, Long userId) {
        editableMonitor(id, userId);
        for (var page : statusPages.findByMonitorsId(id)) {
            page.getMonitors().removeIf(monitor -> monitor.getId().equals(id));
        }
        statusPages.flush();
        maintenance.removeMonitor(id);
        incidents.deleteByMonitorId(id);
        monitorRepository.deleteById(id);
        pingLogRepository.deleteByMonitorId(id); // deletes the logs from mongodb (using id)
    }

    @Transactional(readOnly = true)
    public List<Long> getIntegrationIds(Long id, Long userId) {
        return editableMonitor(id, userId).getIntegrations().stream().map(item -> item.getId()).sorted().toList();
    }

    private Monitor editableMonitor(Long id, Long userId) {
        Monitor monitor = monitorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found"));
        if (monitor.getOwnerId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found");
        }
        teams.requireAccess(monitor.getOwnerId(), userId, true);
        return monitor;
    }

}
