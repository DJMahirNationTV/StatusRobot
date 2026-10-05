package com.djmahirnationtv.status.backend.monitor;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.dto.MonitorResponse;
import com.djmahirnationtv.status.backend.monitor.dto.CreateMonitorRequest;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final PingLogRepository pingLogRepository;

    public MonitorService(MonitorRepository monitorRepository, PingLogRepository pingLogRepository) {
        this.monitorRepository = monitorRepository;
        this.pingLogRepository = pingLogRepository;
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
    public MonitorResponse createMonitor(CreateMonitorRequest req, Long ownerId) {
        Monitor monitor = new Monitor(
            req.name(),
            req.url(),
            req.httpMethod(),
            req.intervalSeconds(),
            req.timeoutSeconds()
        );
        monitor.setOwnerId(ownerId);
        monitor.setStatus(MonitorStatus.UP);
        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public MonitorResponse togglePause(Long id, Long ownerId) {
        Monitor monitor = ownedMonitor(id, ownerId);

        if (monitor.getStatus() == MonitorStatus.PAUSED) {
            monitor.setStatus(MonitorStatus.UP);
        } else {
            monitor.setStatus(MonitorStatus.PAUSED);
        }

        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public void deleteMonitor(Long id, Long ownerId) {
        ownedMonitor(id, ownerId);
        monitorRepository.deleteById(id);
        pingLogRepository.deleteByMonitorId(id); // deletes the logs from mongodb (using id)
    }

    private Monitor ownedMonitor(Long id, Long ownerId) {
        return monitorRepository.findById(id)
                .filter(monitor -> ownerId.equals(monitor.getOwnerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found"));
    }

}
