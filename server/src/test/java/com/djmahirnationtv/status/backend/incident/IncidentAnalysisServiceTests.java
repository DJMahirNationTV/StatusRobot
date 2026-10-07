package com.djmahirnationtv.status.backend.incident;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.http.client.MockClientHttpRequest;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IncidentAnalysisServiceTests {
    private static final String ANSWER = """
            {"status":"completed","output":[{"type":"reasoning"},{"type":"message","role":"assistant",
            "content":[{"type":"output_text","text":"The check received HTTP 503."},
            {"type":"output_text","text":"Check the service logs. The cause is not confirmed."}]}]}
            """;

    private IncidentService.IncidentResponse incident() {
        return new IncidentService.IncidentResponse(1L, 2L, "Private monitor", "HTTP check returned 503.",
                Instant.parse("2026-10-07T08:00:00Z"), null, "Private title", false, false,
                Incident.Stage.INVESTIGATING, List.of(new IncidentService.UpdateResponse(
                Incident.Stage.INVESTIGATING, "Private note with secret", Instant.now())));
    }

    @Test
    void sendsOnlyApprovedFactsAndContextAndReadsEveryOutputText() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new IncidentAnalysisService("test-key", "test-model", builder.build(), Clock.systemUTC());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> {
                    var body = JsonMapper.builder().build().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("store").asBoolean()).isFalse();
                    assertThat(body.path("max_output_tokens").asInt()).isEqualTo(800);
                    assertThat(body.path("input").asString()).contains("503", "The deployment just changed.")
                            .doesNotContain("Private", "secret", "test-key");
                })
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
        var result = service.analyze(incident(), 7L, true, "The deployment just changed.");
        assertThat(result.text()).isEqualTo("The check received HTTP 503.\n\nCheck the service logs. The cause is not confirmed.");
        assertThat(result.model()).isEqualTo("test-model");
        server.verify();
    }

    @Test
    void requiresConsentAndConfigurationWithoutSendingARequest() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new IncidentAnalysisService("", "test-model", builder.build(), Clock.systemUTC());
        assertThat(service.enabled()).isFalse();
        assertThatThrownBy(() -> service.analyze(incident(), 1L, false, ""))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> service.analyze(incident(), 1L, true, ""))
                .hasMessageContaining("503");
        server.verify();
    }

    @Test
    void limitsEachUserAndResetsTheWindowAfterAnHour() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var clock = mock(Clock.class);
        Instant start = Instant.parse("2026-10-07T08:00:00Z");
        when(clock.instant()).thenReturn(start);
        var service = new IncidentAnalysisService("test-key", "test-model", builder.build(), clock);
        server.expect(manyTimes(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
        for (int i = 0; i < 5; i++) service.analyze(incident(), 1L, true, "");
        assertThatThrownBy(() -> service.analyze(incident(), 1L, true, "")).hasMessageContaining("429");
        service.analyze(incident(), 2L, true, "");
        when(clock.instant()).thenReturn(start.plusSeconds(3600));
        assertThat(service.analyze(incident(), 1L, true, "").text()).isNotBlank();
        server.verify();
    }

    @Test
    void capsTotalRequestsAcrossAccounts() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new IncidentAnalysisService("test-key", "test-model", builder.build(), Clock.systemUTC());
        server.expect(manyTimes(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
        for (long owner = 1; owner <= 100; owner++) service.analyze(incident(), owner, true, "");
        assertThatThrownBy(() -> service.analyze(incident(), 101L, true, "")).hasMessageContaining("429");
        server.verify();
    }

    @Test
    void upstreamErrorsNeverExposeKeysOrProviderErrorDetails() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new IncidentAnalysisService("test-key", "test-model", builder.build(), Clock.systemUTC());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider details and test-key"));
        assertThatThrownBy(() -> service.analyze(incident(), 1L, true, ""))
                .hasMessageContaining("502").hasMessageNotContaining("test-key").hasMessageNotContaining("private provider");
        server.verify();
    }

    @Test
    void rejectsIncompleteRefusedEmptyAndMalformedResultsAndUnknownFailures() {
        var json = JsonMapper.builder().build();
        for (String response : List.of("{}", "{\"status\":\"incomplete\"}", "{\"status\":\"completed\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"  \"}]}]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\"}]}]}")) {
            assertThatThrownBy(() -> IncidentAnalysisService.readText(json.readTree(response))).hasMessageContaining("502");
        }
        assertThat(IncidentAnalysisService.safeFailure("https://internal.example/?token=secret")).doesNotContain("secret", "internal");
    }
}
