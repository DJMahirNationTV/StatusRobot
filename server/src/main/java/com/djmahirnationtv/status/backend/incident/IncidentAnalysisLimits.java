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
    private static final int REQUESTS_PER_USER = 5;
    private static final int REQUESTS_PER_SERVER = 100;
    private static final int MAX_RUNNING_REQUESTS = 3;

    private final Clock clock;
    private final Map<Long, Integer> requestsByUser = new HashMap<>();
    private Instant resetAt;
    private int totalRequests;
    private int runningRequests;

    @Autowired
    public IncidentAnalysisLimits() {
        this(Clock.systemUTC());
    }

    IncidentAnalysisLimits(Clock clock) {
        this.clock = clock;
        resetAt = clock.instant().plusSeconds(3600);
    }

    public synchronized void startRequest(Long ownerId) {
        if (runningRequests >= MAX_RUNNING_REQUESTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Analysis is busy. Please try again shortly.");
        }

        Instant now = clock.instant();
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

        requestsByUser.put(ownerId, userRequests + 1);
        totalRequests++;
        runningRequests++;
    }

    public synchronized void finishRequest() {
        runningRequests--;
    }
}
