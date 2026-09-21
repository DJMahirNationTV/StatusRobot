package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.model.PingLog;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Service
public class PingExecutionService {
    private static final Logger log = LoggerFactory.getLogger(PingExecutionService.class);
    private final RestClient pingRestClient;
    private final PingLogRepository pingLogRepository;
    private final MonitorRepository monitorRepository;

    public PingExecutionService(RestClient pingRestClient, PingLogRepository pingLogRepository, MonitorRepository monitorRepository) {
        this.pingRestClient = pingRestClient;
        this.pingLogRepository = pingLogRepository;
        this.monitorRepository = monitorRepository;
    }

    @Transactional
    public void ping(Monitor monitor) {
        long start = System.currentTimeMillis();
        int statusCode = 0;
        boolean success = false;
        String errorMessage = null;

        try {
            HttpMethod method = HttpMethod.valueOf(monitor.getHttpMethod().toUpperCase());

            var response = pingRestClient.method(method)
                    .uri(monitor.getUrl())
                    .exchange((req, res) -> {
                        return new PingResult(res.getStatusCode().value());
                    });

            statusCode = response.statusCode();
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
        monitor.setLastCheckedAt(Instant.now());
        if (!success) {
            monitor.setStatus(MonitorStatus.DOWN);
        } else if (responseTime > 1500) {
            monitor.setStatus(MonitorStatus.DEGRADED); // for now, when responsetime is slower than 1.5s, it will be flagged
        } else {
            monitor.setStatus(MonitorStatus.UP);
        }

        monitorRepository.save(monitor);

        log.info("Pinged [{}] {} -> status: {}, latency: {}ms",
                monitor.getName(), monitor.getUrl(), statusCode, responseTime);
    }

    private record PingResult(int statusCode) {}
}