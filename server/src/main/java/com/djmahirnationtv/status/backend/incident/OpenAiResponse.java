package com.djmahirnationtv.status.backend.incident;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponse(String status,
                             @JsonProperty("incomplete_details") IncompleteDetails incompleteDetails,
                             List<Message> output) {

    public String answerText() {
        if ("incomplete".equals(status) && incompleteDetails != null
                && "max_output_tokens".equals(incompleteDetails.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "OpenAI reached the analysis token limit. Try shorter context. No complete analysis was returned.");
        }
        if (!"completed".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "OpenAI returned an incomplete analysis. Please try again later.");
        }

        List<String> paragraphs = new ArrayList<>();
        if (output != null) {
            for (Message message : output) {
                if (message != null && message.isAssistantMessage()) {
                    paragraphs.addAll(message.textParts());
                }
            }
        }
        String answer = String.join("\n\n", paragraphs);
        if (answer.isBlank() || answer.length() > 12_000) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "OpenAI returned no usable analysis. Please try again later.");
        }
        return answer.strip();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncompleteDetails(String reason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String type, String role, List<Content> content) {
        boolean isAssistantMessage() {
            return "message".equals(type) && "assistant".equals(role);
        }

        List<String> textParts() {
            List<String> parts = new ArrayList<>();
            if (content == null) {
                return parts;
            }
            for (Content part : content) {
                if (part == null) {
                    continue;
                }
                if ("refusal".equals(part.type())) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "OpenAI could not provide an analysis for this incident.");
                }
                if ("output_text".equals(part.type()) && part.text() != null) {
                    parts.add(part.text());
                }
            }
            return parts;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(String type, String text) {}
}
