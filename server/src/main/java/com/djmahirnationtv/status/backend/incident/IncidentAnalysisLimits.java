package com.djmahirnationtv.status.backend.incident;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class IncidentAnalysisLimits {
    // C.DE: Diese eigenen Limits gelten für KI-Incident-Analysen, nicht für normale Monitor-Checks.
    private static final int REQUESTS_PER_USER = 5;
    private static final int REQUESTS_PER_SERVER = 100;
    private static final int MAX_RUNNING_REQUESTS = 3;

    // C.DE: Clock liefert die aktuelle Zeit. Im Test können wir eine kontrollierte Uhr übergeben.
    private final Clock clock;
    private final Map<Long, Integer> requestsByUser = new HashMap<>();
    private Instant resetAt;
    private int totalRequests;
    // C.DE: Dieser Zähler zählt laufende Analysen aller Accounts auf dieser Backend-Instanz.
    private int runningRequests;

    @Autowired
    public IncidentAnalysisLimits() {
        this(Clock.systemUTC());
    }

    IncidentAnalysisLimits(Clock clock) {
        this.clock = clock;
        resetAt = clock.instant().plusSeconds(3600);
    }

    // C.DE: synchronized schützt Prüfen und Erhöhen gemeinsam, damit zwei Requests das Limit nicht umgehen.
    // C.DE: ownerId ist hier die ID des anfragenden Accounts, auch wenn ein Editor für einen anderen Workspace handelt.
    public synchronized void startRequest(Long ownerId) {
        if (runningRequests >= MAX_RUNNING_REQUESTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Analysis is busy. Please try again shortly.");
        }

        Instant now = clock.instant();
        // C.DE: Bei der nächsten Anfrage mit freiem Analyseplatz werden abgelaufene Stundenzähler zurückgesetzt.
        if (!now.isBefore(resetAt)) {
            requestsByUser.clear();
            totalRequests = 0;
            resetAt = now.plusSeconds(3600);
        }

        int userRequests = requestsByUser.getOrDefault(ownerId, 0);
        if (userRequests >= REQUESTS_PER_USER || totalRequests >= REQUESTS_PER_SERVER) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "The hourly analysis limit has been reached. Please try again later.");
        }

        // C.DE: Ein gestarteter Versuch zählt zum Stundenlimit, auch wenn OpenAI später einen Fehler liefert.
        requestsByUser.put(ownerId, userRequests + 1);
        totalRequests++;
        runningRequests++;
    }

    // C.DE: Nur der laufende Platz wird freigegeben. Die Zahl der Versuche im Stundenfenster bleibt erhalten.
    public synchronized void finishRequest() {
        runningRequests--;
    }
}
