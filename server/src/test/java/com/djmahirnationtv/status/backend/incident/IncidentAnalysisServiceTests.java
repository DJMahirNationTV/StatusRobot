package com.djmahirnationtv.status.backend.incident;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.http.client.MockClientHttpRequest;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
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

    private IncidentAnalysisService service(String apiKey, String model, RestClient client, Clock clock) {
        return new IncidentAnalysisService(new OpenAiClient(apiKey, model, client), new IncidentAnalysisLimits(clock));
    }

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
        var service = service("test-key", "test-model", builder.build(), Clock.systemUTC());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(request -> {
                    var body = JsonMapper.builder().build().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("store").asBoolean()).isFalse();
                    assertThat(body.path("max_output_tokens").asInt()).isEqualTo(800);
                    assertThat(body.has("reasoning")).isFalse();
                    assertThat(body.has("text")).isFalse();
                    try (var input = new ClassPathResource("openai/incident-analysis.json").getInputStream()) {
                        var prompt = JsonMapper.builder().build().readTree(input);
                        assertThat(body.path("instructions").asString()).startsWith(prompt.path("instructions").get(0).asString());
                    }
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
    void nanoAliasAndSnapshotUseMinimalReasoningAndLeaveRoomForTheAnswer() {
        for (String model : List.of("gpt-5-nano", "gpt-5-nano-2025-08-07")) {
            var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
            var server = MockRestServiceServer.bindTo(builder).build();
            var service = service("test-key", model, builder.build(), Clock.systemUTC());
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andExpect(request -> {
                        var body = JsonMapper.builder().build().readTree(((MockClientHttpRequest) request).getBodyAsString());
                        assertThat(body.path("model").asString()).isEqualTo(model);
                        assertThat(body.path("reasoning").path("effort").asString()).isEqualTo("minimal");
                        assertThat(body.path("text").path("verbosity").asString()).isEqualTo("low");
                        assertThat(body.path("max_output_tokens").asInt()).isEqualTo(2000);
                        assertThat(body.path("store").asBoolean()).isFalse();
                        assertThat(body.has("temperature")).isFalse();
                    })
                    .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
            assertThat(service.analyze(incident(), 1L, true, "").model()).isEqualTo(model);
            server.verify();
        }
    }

    @Test
    void tokenExhaustionExplainsTheLimitWithoutReturningPartialTextOrRetrying() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = service("test-key", "gpt-5-nano-2025-08-07", builder.build(), Clock.systemUTC());
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("""
                {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},
                "output":[{"type":"message","role":"assistant","content":[{"type":"output_text",
                "text":"Incomplete suggestion that should not be shown"}]}]}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.analyze(incident(), 1L, true, ""))
                .hasMessageContaining("analysis token limit").hasMessageNotContaining("Incomplete suggestion");
        server.verify();
    }

    @Test
    void requiresConsentAndConfigurationWithoutSendingARequest() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = service("", "test-model", builder.build(), Clock.systemUTC());
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
        var service = service("test-key", "test-model", builder.build(), clock);
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
        var service = service("test-key", "test-model", builder.build(), Clock.systemUTC());
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
        var service = service("test-key", "test-model", builder.build(), Clock.systemUTC());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider details and test-key"));
        assertThatThrownBy(() -> service.analyze(incident(), 1L, true, ""))
                .hasMessageContaining("502").hasMessageNotContaining("test-key").hasMessageNotContaining("private provider");
        server.verify();
    }

    @Test
    void unreadableOrMissingProviderResponsesReturnASafeError() {
        for (String response : List.of("not JSON with private provider details",
                "{\"status\":\"completed\",\"output\":\"private provider details\"}", "")) {
            var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
            var server = MockRestServiceServer.bindTo(builder).build();
            var service = service("test-key", "test-model", builder.build(), Clock.systemUTC());
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> service.analyze(incident(), 1L, true, ""))
                    .hasMessageContaining("502").hasMessageNotContaining("test-key")
                    .hasMessageNotContaining("private provider details");
            server.verify();
        }
    }

    @Test
    void rejectsIncompleteRefusedEmptyAndMalformedResultsAndUnknownFailures() {
        var json = JsonMapper.builder().build();
        for (String response : List.of("{}", "{\"status\":\"incomplete\"}", "{\"status\":\"completed\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"  \"}]}]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\"}]}]}")) {
            assertThatThrownBy(() -> json.readValue(response, OpenAiResponse.class).answerText()).hasMessageContaining("502");
        }
        assertThat(OpenAiClient.checkResultForOpenAi("https://internal.example/?token=secret")).doesNotContain("secret", "internal");
        assertThat(OpenAiClient.checkResultForOpenAi(null)).isEqualTo("No automatic failure details available.");
        assertThat(OpenAiClient.checkResultForOpenAi("No HTTP response was received.")).isEqualTo("No HTTP response was received.");
    }

    @Test
    void loadsEditedInstructionsFromJsonRatherThanHardcodedText() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var prompt = new ByteArrayResource("""
                {"instructions":["Explain the outage simply.","Suggest two safe checks."]}
                """.getBytes(StandardCharsets.UTF_8));
        var client = new OpenAiClient("test-key", "test-model", builder.build(), prompt);
        var service = new IncidentAnalysisService(client, new IncidentAnalysisLimits());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(request -> {
                    var body = JsonMapper.builder().build().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.path("instructions").asString())
                            .isEqualTo("Explain the outage simply.\nSuggest two safe checks.");
                })
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));
        assertThat(service.analyze(incident(), 1L, true, null).text()).isNotBlank();
        server.verify();
    }

    @Test
    void missingOrInvalidPromptFailsWithAClearConfigurationError() {
        var client = mock(RestClient.class);
        assertThatThrownBy(() -> new OpenAiClient("", "test-model", client,
                new ClassPathResource("openai/missing.json")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("incident-analysis.json");
        for (String prompt : List.of("not json", "null", "{}", "{\"instructions\":[]}",
                "{\"instructions\":[null]}", "{\"instructions\":[\"  \"]}")) {
            assertThatThrownBy(() -> new OpenAiClient("", "test-model", client,
                    new ByteArrayResource(prompt.getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void responseMappingIgnoresExtraFieldsAndKeepsTextFromEveryAssistantMessage() {
        var response = JsonMapper.builder().build().readValue("""
                {"id":"response-id","status":"completed","usage":{"output_tokens":10},"output":[
                {"type":"reasoning","summary":[]},
                {"type":"message","role":"user","content":[{"type":"output_text","text":"Ignore this"}]},
                {"type":"message","role":"assistant","id":"first","content":[
                {"type":"output_text","text":"First paragraph","annotations":[]}]},
                {"type":"message","role":"assistant","id":"second","content":[
                {"type":"output_text","text":"Second paragraph"}]}]}
                """, OpenAiResponse.class);
        assertThat(response.answerText()).isEqualTo("First paragraph\n\nSecond paragraph");
    }

    @Test
    void rejectsMissingTextAndOversizedAnswers() {
        var json = JsonMapper.builder().build();
        for (String response : List.of("{\"status\":\"completed\"}",
                "{\"status\":\"completed\",\"output\":[null]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\"}]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[null,{\"type\":\"output_text\"}]}]}")) {
            assertThatThrownBy(() -> json.readValue(response, OpenAiResponse.class).answerText()).hasMessageContaining("502");
        }
        var oversized = new OpenAiResponse("completed", null, List.of(new OpenAiResponse.Message(
                "message", "assistant", List.of(new OpenAiResponse.Content("output_text", "a".repeat(12_001))))));
        assertThatThrownBy(oversized::answerText).hasMessageContaining("502");
    }

    @Test
    void failedRequestsCountTowardTheLimitButDoNotLeaveRunningSlotsOccupied() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = service("test-key", "test-model", builder.build(), Clock.systemUTC());
        server.expect(manyTimes(), requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.analyze(incident(), 1L, true, "")).hasMessageContaining("502");
        }
        assertThatThrownBy(() -> service.analyze(incident(), 1L, true, ""))
                .hasMessageContaining("429").hasMessageContaining("hourly");
        assertThatThrownBy(() -> service.analyze(incident(), 2L, true, "")).hasMessageContaining("502");
        server.verify();
    }
}
