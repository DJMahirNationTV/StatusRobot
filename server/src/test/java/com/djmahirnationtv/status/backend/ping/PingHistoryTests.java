package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.PingHistoryService.HistoryAggregate;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PingHistoryTests {
    @Test
    void calculatesExactUptimeAndKeepsMissingDaysUnknown() {
        var pings = mock(PingLogRepository.class);
        var monitors = mock(MonitorRepository.class);
        when(monitors.existsById(1L)).thenReturn(true);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        var totals = List.of(new HistoryAggregate.Totals(4, 3));
        when(pings.aggregateHistory(eq(1L), any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(new HistoryAggregate(List.of(new HistoryAggregate.Day(today, 4, 3)), List.of(), totals, totals, totals, totals));
        var history = new PingHistoryService(pings, monitors).history(1L, 24);
        assertThat(history.daily()).hasSize(90);
        assertThat(history.daily().getFirst().uptimePercentage()).isNull();
        assertThat(history.daily().getLast().uptimePercentage()).isEqualTo(75.0);
        assertThat(history.periods()).allSatisfy(period -> assertThat(period.uptimePercentage()).isEqualTo(75.0));
    }

    @Test
    void noRecordedChecksDoNotReportPerfectUptime() {
        var pings = mock(PingLogRepository.class);
        var monitors = mock(MonitorRepository.class);
        when(monitors.existsById(1L)).thenReturn(true);
        var history = new PingHistoryService(pings, monitors).history(1L, 6);
        assertThat(history.periods()).allSatisfy(period -> assertThat(period.uptimePercentage()).isNull());
        assertThat(history.daily()).allSatisfy(day -> assertThat(day.checks()).isZero());
        assertThat(history.response()).isEmpty();
    }

    @Test
    void rejectsUnboundedPeriodsAndMissingMonitors() {
        var pings = mock(PingLogRepository.class);
        var service = new PingHistoryService(pings, mock(MonitorRepository.class));
        assertThatThrownBy(() -> service.history(1L, 10000)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.history(1L, 24)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(pings);
    }
}
