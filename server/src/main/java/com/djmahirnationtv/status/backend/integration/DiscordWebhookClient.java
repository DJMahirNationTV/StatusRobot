package com.djmahirnationtv.status.backend.integration;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DiscordWebhookClient {
    private final RestClient client;

    public DiscordWebhookClient() {
        var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public static boolean validUrl(String url) {
        return url != null && url.matches("https://discord\\.com/api/webhooks/[0-9]{1,20}/[A-Za-z0-9_-]{20,200}");
    }

    public boolean send(String url, String content) {
        if (!validUrl(url)) return false;
        try {
            return Boolean.TRUE.equals(client.post().uri(url + "?wait=true").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content, "allowed_mentions", Map.of("parse", List.of())))
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()));
        } catch (Exception exception) {
            // HTTP exceptions can contain the secret webhook URL.
            return false;
        }
    }
}
