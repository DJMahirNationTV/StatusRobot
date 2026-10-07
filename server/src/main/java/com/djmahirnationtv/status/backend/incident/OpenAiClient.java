package com.djmahirnationtv.status.backend.incident;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient {
    private final String apiKey;
    private final String model;
    private final String instructions;
    private final RestClient httpClient;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public OpenAiClient(@Value("${app.openai.api-key:}") String apiKey,
                        @Value("${app.openai.model:gpt-4.1-mini}") String model) {
        this(apiKey, model, createHttpClient());
    }

    OpenAiClient(String apiKey, String model, RestClient httpClient) {
        this(apiKey, model, httpClient, new ClassPathResource("openai/incident-analysis.json"));
    }

    OpenAiClient(String apiKey, String model, RestClient httpClient, Resource promptFile) {
        this.apiKey = apiKey.strip();
        this.model = model.strip();
        this.httpClient = httpClient;
        this.instructions = loadInstructions(promptFile);
    }

    private static RestClient createHttpClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().baseUrl("https://api.openai.com/v1").requestFactory(factory).build();
    }

    public boolean enabled() {
        return !apiKey.isEmpty() && !model.isEmpty();
    }

    public String model() {
        return model;
    }

    public String analyze(IncidentService.IncidentResponse incident, String context) {
        try {
            OpenAiResponse response = httpClient.post().uri("/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createRequest(incident, context))
                    .retrieve().body(OpenAiResponse.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "OpenAI returned an incomplete analysis. Please try again later.");
            }
            return response.answerText();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "OpenAI could not complete this request. Check the server configuration or try again later.");
        }
    }

    private Map<String, Object> createRequest(IncidentService.IncidentResponse incident, String context) {
        boolean nano = model.equals("gpt-5-nano") || model.startsWith("gpt-5-nano-");
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("store", false);
        request.put("max_output_tokens", nano ? 2000 : 800);
        request.put("instructions", instructions);
        request.put("input", incidentFacts(incident, context));
        if (nano) {
            request.put("reasoning", Map.of("effort", "minimal"));
            request.put("text", Map.of("verbosity", "low"));
        }
        return request;
    }

    private String incidentFacts(IncidentService.IncidentResponse incident, String context) {
        Map<String, String> facts = new HashMap<>();
        facts.put("source", incident.manual() ? "manual report" : "automatic HTTP check");
        facts.put("failure", checkResultForOpenAi(incident.cause()));
        facts.put("progress", incident.stage().name());
        facts.put("started_at", incident.startedAt().toString());
        facts.put("resolved_at", incident.resolvedAt() == null ? "ongoing" : incident.resolvedAt().toString());
        facts.put("optional_context", context == null ? "" : context.strip());
        return json.writeValueAsString(facts);
    }

    static String checkResultForOpenAi(String cause) {
        // Only known check messages are sent, not raw errors that could contain secrets.
        if ("No HTTP response was received.".equals(cause)
                || (cause != null && cause.matches("HTTP check returned [1-5][0-9]{2}\\."))) {
            return cause;
        }
        return "No automatic failure details available.";
    }

    private record Prompt(List<String> instructions) {}

    private String loadInstructions(Resource promptFile) {
        try (var input = promptFile.getInputStream()) {
            Prompt prompt = json.readValue(input, Prompt.class);
            if (prompt == null || prompt.instructions() == null || prompt.instructions().isEmpty()) {
                throw new IllegalStateException("The incident analysis JSON needs an instructions list.");
            }
            for (String line : prompt.instructions()) {
                if (line == null || line.isBlank()) {
                    throw new IllegalStateException("Incident analysis instructions cannot contain empty lines.");
                }
            }
            return String.join("\n", prompt.instructions());
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("Could not read openai/incident-analysis.json. Check that the file exists and contains valid JSON.");
        }
    }
}
