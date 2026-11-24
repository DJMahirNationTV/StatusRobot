package com.djmahirnationtv.status.backend.incident;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentAnalysisService {
    private final OpenAiClient openAi;
    private final IncidentAnalysisLimits limits;

    // C.DE: Spring stellt die benötigten Objekte bereit und übergibt sie über den Konstruktor.
    public IncidentAnalysisService(OpenAiClient openAi, IncidentAnalysisLimits limits) {
        this.openAi = openAi;
        this.limits = limits;
    }

    public boolean enabled() {
        return openAi.enabled();
    }

    public record Analysis(String text, String model) {}

    public Analysis analyze(IncidentService.IncidentResponse incident, Long ownerId, boolean consent, String context) {
        if (!consent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirm that you want to send these details to OpenAI.");
        }
        if (!enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI support is not configured on this server.");
        }

        // C.DE: Wir reservieren einen Analyseplatz. Die langsame OpenAI-Anfrage läuft außerhalb der Zählersperre.
        limits.startRequest(ownerId);
        try {
            String answer = openAi.analyze(incident, context);
            return new Analysis(answer, openAi.model());
        } finally {
            // C.DE: finally gibt den Platz auch bei einem Fehler frei, damit der Zähler nicht dauerhaft zu hoch bleibt.
            limits.finishRequest();
        }
    }
}
