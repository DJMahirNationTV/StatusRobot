package com.djmahirnationtv.status.backend.integration;

public record MonitorStatusChanged(Long monitorId, Long ownerId, String name, boolean down) {}
