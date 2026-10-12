package com.djmahirnationtv.status.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorsTests {
    private final ApiErrors errors = new ApiErrors();

    @Test
    void keepsTheStatusAndMessage() {
        var response = errors.failed(new ResponseStatusException(HttpStatus.FORBIDDEN, "Viewers cannot edit."));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "Viewers cannot edit.");
    }

    @Test
    void handlesAnExceptionWithoutAReason() {
        var response = errors.failed(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        assertThat(response.getBody()).containsEntry("message", "The request could not be completed.");
    }
}
