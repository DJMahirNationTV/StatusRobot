package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import com.djmahirnationtv.status.backend.monitor.model.MonitorStatus;
import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.model.PingLog;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PingExecutionServiceTests {
    @Test
    void usesTheMonitorsTimeoutWhenTheWebsiteDoesNotRespond() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var monitors = mock(MonitorRepository.class);
            var pings = mock(PingLogRepository.class);
            var service = new PingExecutionService(RestClient.create(), pings, monitors);
            var monitor = new Monitor("Slow website", "http://127.0.0.1:" + server.getAddress().getPort() + "/slow", "GET", 60, 1);
            monitor.setId(1L);

            service.ping(monitor);

            var saved = ArgumentCaptor.forClass(PingLog.class);
            verify(pings).save(saved.capture());
            assertThat(saved.getValue().isSuccessful()).isFalse();
            assertThat(saved.getValue().getErrorMessage()).contains("timed out");
            assertThat(monitor.getStatus()).isEqualTo(MonitorStatus.DOWN);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
