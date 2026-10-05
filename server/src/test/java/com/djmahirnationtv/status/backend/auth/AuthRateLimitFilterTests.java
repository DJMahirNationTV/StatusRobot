package com.djmahirnationtv.status.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTests {
    @Test
    void limitsBothAuthEndpointsPerAddressAndResetsAfterFiveMinutes() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        var filter = new AuthRateLimitFilter(clock);
        for (int i = 0; i < 20; i++) {
            assertThat(call(filter, "POST", i % 2 == 0 ? "/api/auth/login" : "/api/auth/register", "127.0.0.1"))
                    .isEqualTo(204);
        }
        assertThat(call(filter, "POST", "/api/auth/login", "127.0.0.1")).isEqualTo(429);
        assertThat(call(filter, "POST", "/api/auth/login", "127.0.0.2")).isEqualTo(204);
        assertThat(call(filter, "GET", "/api/auth/csrf", "127.0.0.1")).isEqualTo(204);
        assertThat(call(filter, "POST", "/api/auth/logout", "127.0.0.1")).isEqualTo(204);
        when(clock.millis()).thenReturn(300000L);
        assertThat(call(filter, "POST", "/api/auth/login", "127.0.0.1")).isEqualTo(204);
    }

    private int call(AuthRateLimitFilter filter, String method, String path, String address) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(address);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(204));
        return response.getStatus();
    }
}
