package de.kreiscraft.modules.aichat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenRouterClient {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String apiKey;

    public OpenRouterClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be null or empty.");
        }
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String sendPrompt(String model, String userPrompt, String systemPrompt)
            throws IOException, InterruptedException {

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model cannot be null or empty.");
        }

        String jsonPayload = String.format("""
                {
                  "model": "%s",
                  "reasoning": {
                    "effort": "low"
                  },
                  "messages": [
                    {
                      "role": "system",
                      "content": "%s"
                    },
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ]
                }
                """, sanitizeJson(model), sanitizeJson(systemPrompt), sanitizeJson(userPrompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://kreiscraft.de")
                .header("X-Title", "Kreiscraft AI Bot")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return extractContent(response.body());
        }

        throw new IOException(String.format("OpenRouter API request failed [Status %d]: %s",
                response.statusCode(), response.body()));
    }

    private String sanitizeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractContent(String jsonResponseBody) throws IOException {
        JsonNode root = MAPPER.readTree(jsonResponseBody);
        return root.path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
    }
}
