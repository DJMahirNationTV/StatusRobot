package com.djmahirnationtv.status.backend.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import org.hibernate.validator.constraints.URL;

public record MonitorRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    String name,
    @NotBlank(message = "URL is required")
    @URL(message = "The URL must be a valid HTTP or HTTPS URL!")
    @Pattern(regexp = "^https?://.*", message = "Use an HTTP or HTTPS URL")
    @Size(max = 255, message = "URL must be at most 255 characters")
    String url,

    @NotBlank(message = "Method is required")
    @Pattern(regexp = "^(GET|POST|HEAD)$", message = "Method must be GET, POST, or HEAD")
    String httpMethod,

    @Min(value = 10, message = "Check interval must be at least 10 seconds")
    @Max(value = 86400, message = "Check interval must be at most 86400 seconds")
    int intervalSeconds,
    @Min(value = 1, message = "Timeout must be at least 1 second")
    @Max(value = 30, message = "Timeout must be at most 30 seconds")
    int timeoutSeconds
) {
    @AssertTrue(message = "Timeout cannot be longer than the check interval")
    public boolean isTimeoutWithinInterval() {
        return timeoutSeconds <= intervalSeconds;
    }
}
