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
    public MonitorResponse createMonitor(CreateMonitorRequest req) {
        Monitor monitor = new Monitor(
            req.name(),
            req.url(),
            req.httpMethod(),
            req.intervalSeconds(),
            req.timeoutSeconds()
        );
        monitor.setStatus(MonitorStatus.UP); // Start as UP until first scheduled ping
        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public MonitorResponse togglePause(Long id) {
        Monitor monitor = monitorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found"));

        if (monitor.getStatus() == MonitorStatus.PAUSED) {
            monitor.setStatus(MonitorStatus.UP);
        } else {
            monitor.setStatus(MonitorStatus.PAUSED);
        }

        return MonitorResponse.from(monitorRepository.save(monitor));
    }

    @Transactional
    public void deleteMonitor(Long id) {
        if (!monitorRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found");
        }
        monitorRepository.deleteById(id);
        pingLogRepository.deleteByMonitorId(id); // deletes the logs from mongodb (using id)
    }

}