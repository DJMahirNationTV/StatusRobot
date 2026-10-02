package com.djmahirnationtv.status.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(5).toMillis();
    private final Map<String, Attempts> clients = new HashMap<>();
    private final Clock clock;

    public AuthRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !request.getMethod().equals("POST") ||
                !(path.equals("/api/auth/login") || path.equals("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "300");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many attempts. Try again in five minutes.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String address) {
        long now = clock.millis();
        clients.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
        if (!clients.containsKey(address) && clients.size() >= 10000) {
            return false;
        }
        Attempts attempts = clients.computeIfAbsent(address, key -> new Attempts(now));
        return ++attempts.count <= 20;
    }

    private static class Attempts {
        private final long startedAt;
        private int count;

        private Attempts(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
