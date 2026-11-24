package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.model.PingLog;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.Duration;
import org.springframework.context.ApplicationEventPublisher;
import com.djmahirnationtv.status.backend.integration.MonitorStatusChanged;
import com.djmahirnationtv.status.backend.incident.IncidentService;

@Service
public class PingExecutionService {
    private static final Logger log = LoggerFactory.getLogger(PingExecutionService.class);
    private final RestClient pingRestClient;
    private final PingLogRepository pingLogRepository;
    private final MonitorRepository monitorRepository;
    private final ApplicationEventPublisher events;
    private final IncidentService incidents;

    public PingExecutionService(RestClient pingRestClient, PingLogRepository pingLogRepository, MonitorRepository monitorRepository,
                                ApplicationEventPublisher events, IncidentService incidents) {
        this.pingRestClient = pingRestClient;
        this.pingLogRepository = pingLogRepository;
        this.monitorRepository = monitorRepository;
        this.events = events;
        this.incidents = incidents;
    }

    @Transactional
    public void ping(Monitor monitor) {
        // C.DE: Die Transaktion betrifft MySQL. HTTP-Anfragen und bereits gespeicherte MongoDB-Logs werden nicht zurückgerollt.
        MonitorStatus previousStatus = monitor.getStatus();
        long start = System.currentTimeMillis();
        int statusCode = 0;
        boolean success = false;
        String errorMessage = null;

        try {
            HttpMethod method = HttpMethod.valueOf(monitor.getHttpMethod().toUpperCase());

            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(monitor.getTimeoutSeconds()));
            factory.setReadTimeout(Duration.ofSeconds(monitor.getTimeoutSeconds()));
            var response = pingRestClient.mutate().requestFactory(factory).build().method(method)
                    .uri(monitor.getUrl())
                    .exchange((req, res) -> {
                        return new PingResult(res.getStatusCode().value());
                    });

            statusCode = response.statusCode();
            // C.DE: HTTP-Codes von 200 bis 399 gelten hier als erfolgreich. Netzwerkfehler werden als fehlgeschlagen erfasst.
            success = statusCode >= 200 && statusCode < 400;
        } catch (Exception ex) {
            errorMessage = ex.getMessage();
            statusCode = 0;
            success = false;
        }

        long responseTime = System.currentTimeMillis() - start;

        // saving the telemtry to the mongodb
        PingLog pingLog = new PingLog(monitor.getId(), statusCode, responseTime, success, errorMessage);
        pingLogRepository.save(pingLog);

        // we update the state in mysql basically..
        // C.DE: Fehlgeschlagen bedeutet DOWN. Erfolgreich, aber langsamer als 1500 ms bedeutet DEGRADED, sonst UP.
        monitor.setLastCheckedAt(Instant.now());
        if (!success) {
            monitor.setStatus(MonitorStatus.DOWN);
        } else if (responseTime > 1500) {
            monitor.setStatus(MonitorStatus.DEGRADED); // for now, when responsetime is slower than 1.5s, it will be flagged
        } else {
            monitor.setStatus(MonitorStatus.UP);
        }

        monitorRepository.saveAndFlush(monitor);
        incidents.recordCheck(monitor, statusCode);
        boolean down = monitor.getStatus() == MonitorStatus.DOWN;
        // C.DE: Ein Alert-Ereignis entsteht beim Wechsel zu DOWN oder bei Erholung, nicht bei jedem weiteren gleichen Ergebnis.
        if (previousStatus != MonitorStatus.PAUSED && down != (previousStatus == MonitorStatus.DOWN)) {
            events.publishEvent(new MonitorStatusChanged(monitor.getId(), monitor.getOwnerId(), monitor.getName(), down));
        }

        log.info("Pinged [{}] {} -> status: {}, latency: {}ms",
                monitor.getName(), monitor.getUrl(), statusCode, responseTime);
    }

    private record PingResult(int statusCode) {}
}
