package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.maintenance.MaintenanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;

@Component
public class PingScheduler {
    private static final Logger log = LoggerFactory.getLogger(PingScheduler.class);
    private final MonitorRepository monitorRepository;
    private final PingExecutionService pingExecutionService;
    private final MaintenanceService maintenance;

    public PingScheduler(MonitorRepository monitorRepository, PingExecutionService pingExecutionService, MaintenanceService maintenance) {
        this.monitorRepository = monitorRepository;
        this.pingExecutionService = pingExecutionService;
        this.maintenance = maintenance;
    }

    @Scheduled(fixedDelay = 10_000) // polls basically every 10 seconds
    public void schedulePings() {
        List<Monitor> activeMonitors = monitorRepository.findByStatusNot(MonitorStatus.PAUSED); //when the monitors are paused/stopped, they will NOT get monitored at all!
        Instant now = Instant.now();

        for (Monitor monitor : activeMonitors) {
            if (isDueForPing(monitor, now)) {
                // Skip planned work without changing the monitor's manual pause setting.
                if (maintenance.isActive(monitor.getId(), Instant.now())) continue;
                try {
                    pingExecutionService.ping(monitor);
                } catch (ObjectOptimisticLockingFailureException exception) {
                    log.debug("Monitor {} changed during its check; keeping the newer settings", monitor.getId());
                }
            }
        }
    }

    private boolean isDueForPing(Monitor monitor, Instant now) {
        if (monitor.getLastCheckedAt() == null) {
            return true; // checks if it never was pinged before, on create, it will do "true" state ASAP, so we do not have a null
        }
        long secondsSinceLastCheck = now.getEpochSecond() - monitor.getLastCheckedAt().getEpochSecond();
        return secondsSinceLastCheck >= monitor.getIntervalSeconds();
    }
}
