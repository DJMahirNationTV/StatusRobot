package com.djmahirnationtv.status.backend.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

public record CreateMonitorRequest(
    @NotBlank(message = "Name is required")
    String name,
    @NotBlank(message = "URL is required")
    @URL(message = "The URL must be a valid HTTP or HTTPS URL!")
    String url,

    @Pattern(regexp = "^(GET|POST|HEAD)$", message = "Method must be GET, POST, or HEAD")
    String httpMethod,

    int intervalSeconds,
    int timeoutSeconds
) {}
