package com.djmahirnationtv.status.backend.ping;

import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import com.djmahirnationtv.status.backend.ping.repository.PingLogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PingHistoryService {
    private final PingLogRepository pings;
    private final MonitorRepository monitors;
    public PingHistoryService(PingLogRepository pings, MonitorRepository monitors) {
        this.pings = pings;
        this.monitors = monitors;
    }

    public MonitorHistoryResponse history(Long id, int hours) {
        // C.DE: Je länger der Chart-Zeitraum, desto größer die Zeitgruppen. So müssen nicht alle einzelnen Checks gezeichnet werden.

        // Der Code prüft Zeitraum und Monitor, 
        // legt die Größe der Chart-Zeitgruppen fest und lässt MongoDB die Messdaten zusammenfassen. 
        // Ein fehlendes Ergebnis wird durch leere Listen ersetzt
        
        int bucketMinutes = switch (hours) {
            // Das sind Zeitgruppen. 
            // Beispielsweise werden die erfolgreichen Antwortzeiten 
            // innerhalb von zwei Minuten zu einem Durchschnitt zusammengefasst
            case 6 -> 2; // 6 hours = 2 minute buckets
            case 24 -> 10; // 24 hours = 10 minute buckets
            case 168 -> 60; // 168 hours = 1 hour buckets
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose 6, 24 or 168 hours.");
        };
        if (!monitors.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Monitor not found");
        Instant now = Instant.now();
        var data = pings.aggregateHistory(id, now.minus(90, ChronoUnit.DAYS), now,
                now.minus(hours, ChronoUnit.HOURS), bucketMinutes, now.minus(1, ChronoUnit.DAYS),
                now.minus(7, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS));
        if (data == null) data = new HistoryAggregate(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        // C.DE: Wir bauen eine Suche nach Datum auf, damit die Tagesdaten im folgenden Durchlauf direkt gefunden werden.
        Map<String, HistoryAggregate.Day> days = data.daily().stream().collect(Collectors.toMap(HistoryAggregate.Day::date, day -> day));
        var daily = new ArrayList<MonitorHistoryResponse.Day>();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        // C.DE: Wir liefern 90 UTC-Tage in Reihenfolge, auch Tage ohne Messung. Fehlende Checks werden nicht als Erfolg erfunden.
        for (int offset = 89; offset >= 0; offset--) {
            String date = today.minusDays(offset).toString();
            var day = days.get(date);
            long checks = day == null ? 0 : day.checks();
            long successful = day == null ? 0 : day.successful();
            daily.add(new MonitorHistoryResponse.Day(date, checks, successful, percentage(checks, successful)));
        }
        return new MonitorHistoryResponse(now, hours, bucketMinutes, List.of(
                period(1, data.day()), period(7, data.week()), period(30, data.month()), period(90, data.quarter())), daily, data.response());
    }

    private MonitorHistoryResponse.Period period(int days, List<HistoryAggregate.Totals> values) {
        var totals = values.isEmpty() ? new HistoryAggregate.Totals(0, 0) : values.getFirst();
        return new MonitorHistoryResponse.Period(days, totals.checks(), totals.successful(), percentage(totals.checks(), totals.successful()));
    }

    // C.DE: Uptime ist erfolgreiche Checks geteilt durch alle Checks mal 100. Ohne Messungen liefern wir null statt 100 Prozent.
    private Double percentage(long checks, long successful) { return checks == 0 ? null : 100.0 * successful / checks; }

    public record HistoryAggregate(List<Day> daily, List<Point> response,
                                   List<Totals> day, List<Totals> week, List<Totals> month, List<Totals> quarter) {
        public record Totals(long checks, long successful) {}
        public record Day(String date, long checks, long successful) {}
        public record Point(Instant timestamp, Double responseTimeMs, long checks, long successful) {}
    }

    public record MonitorHistoryResponse(Instant generatedAt, int hours, int bucketMinutes,
                                         List<Period> periods, List<Day> daily, List<HistoryAggregate.Point> response) {
        public record Period(int days, long checks, long successful, Double uptimePercentage) {}
        public record Day(String date, long checks, long successful, Double uptimePercentage) {}
    }
}
