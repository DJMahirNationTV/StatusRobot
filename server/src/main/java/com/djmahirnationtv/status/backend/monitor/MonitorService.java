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

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final PingLogRepository pingLogRepository;
    private final IntegrationService integrations;
    private final StatusPageRepository statusPages;
    private final IncidentRepository incidents;

    public MonitorService(MonitorRepository monitorRepository, PingLogRepository pingLogRepository, IntegrationService integrations,
                          StatusPageRepository statusPages, IncidentRepository incidents) {
        this.monitorRepository = monitorRepository;
        this.pingLogRepository = pingLogRepository;
        this.integrations = integrations;
        this.statusPages = statusPages;
        this.incidents = incidents;
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

    @Transactional
    public MonitorResponse updateMonitor(Long id, MonitorRequest req, Long ownerId) {
        Monitor monitor = ownedMonitor(id, ownerId);
        boolean targetChanged = !monitor.getUrl().equals(req.url()) || !monitor.getHttpMethod().equals(req.httpMethod());
        monitor.setName(req.name().strip());
        monitor.setUrl(req.url().strip());
        monitor.setHttpMethod(req.httpMethod());
        monitor.setIntervalSeconds(req.intervalSeconds());
        monitor.setTimeoutSeconds(req.timeoutSeconds());
        if (req.integrationIds() != null) {
            var selected = integrations.selection(req.integrationIds(), ownerId);
            monitor.getIntegrations().clear();
            monitor.getIntegrations().addAll(selected);
        }
        if (targetChanged) monitor.setLastCheckedAt(null);
        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public MonitorResponse togglePause(Long id, Long ownerId) {
        Monitor monitor = ownedMonitor(id, ownerId);

        if (monitor.getStatus() == MonitorStatus.PAUSED) {
            monitor.setStatus(MonitorStatus.UP);
            monitor.setLastCheckedAt(null);
        } else {
            monitor.setStatus(MonitorStatus.PAUSED);
        }

        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public void deleteMonitor(Long id, Long ownerId) {
        ownedMonitor(id, ownerId);
        for (var page : statusPages.findByMonitorsId(id)) {
            page.getMonitors().removeIf(monitor -> monitor.getId().equals(id));
        }
        statusPages.flush();
        incidents.deleteByMonitorId(id);
        monitorRepository.deleteById(id);
        pingLogRepository.deleteByMonitorId(id); // deletes the logs from mongodb (using id)
    }

    @Transactional(readOnly = true)
    public List<Long> getIntegrationIds(Long id, Long ownerId) {
        return ownedMonitor(id, ownerId).getIntegrations().stream().map(item -> item.getId()).sorted().toList();
    }

    private Monitor ownedMonitor(Long id, Long ownerId) {
        return monitorRepository.findById(id)
                .filter(monitor -> ownerId.equals(monitor.getOwnerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found"));
    }

}
