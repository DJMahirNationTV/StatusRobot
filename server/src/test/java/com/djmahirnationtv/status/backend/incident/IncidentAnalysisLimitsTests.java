package com.djmahirnationtv.status.backend.incident;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentAnalysisLimitsTests {
    @Test
    void onlyThreeRequestsCanRunAtOnce() {
        var limits = new IncidentAnalysisLimits();
        limits.startRequest(1L);
        limits.startRequest(2L);
        limits.startRequest(3L);
        assertThatThrownBy(() -> limits.startRequest(4L)).hasMessageContaining("429").hasMessageContaining("busy");
        limits.finishRequest();
        assertThatCode(() -> limits.startRequest(4L)).doesNotThrowAnyException();
    }

    @Test
    void hourlyResetDoesNotForgetRequestsStillRunning() {
        var clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-07T08:00:00Z");
        when(clock.instant()).thenReturn(start);
        var limits = new IncidentAnalysisLimits(clock);
        limits.startRequest(1L);
        limits.startRequest(2L);

        when(clock.instant()).thenReturn(start.plusSeconds(3600));
        limits.startRequest(3L);
        assertThatThrownBy(() -> limits.startRequest(4L)).hasMessageContaining("busy");
        limits.finishRequest();
        assertThatCode(() -> limits.startRequest(4L)).doesNotThrowAnyException();
    }

    @Test
    void hourlyLimitRemainsUntilTheResetTime() {
        var clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-07T08:00:00Z");
        when(clock.instant()).thenReturn(start);
        var limits = new IncidentAnalysisLimits(clock);
        for (int i = 0; i < 5; i++) {
            limits.startRequest(1L);
            limits.finishRequest();
        }
        when(clock.instant()).thenReturn(start.plusSeconds(3599));
        assertThatThrownBy(() -> limits.startRequest(1L)).hasMessageContaining("hourly");
        when(clock.instant()).thenReturn(start.plusSeconds(3600));
        assertThatCode(() -> limits.startRequest(1L)).doesNotThrowAnyException();
    }
}
