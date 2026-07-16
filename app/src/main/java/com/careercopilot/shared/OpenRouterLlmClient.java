package com.careercopilot.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.ai.client-type", havingValue = "openrouter")
public class OpenRouterLlmClient implements LlmClient {

    private final RestClient restClient;
    private final String defaultModel;

    public OpenRouterLlmClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.ai.openrouter.api-key:}") String apiKey,
            @Value("${app.ai.openrouter.model:openrouter/free}") String defaultModel,
            @Value("${app.ai.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl) {
        this.defaultModel = defaultModel;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("HTTP-Referer", "http://localhost:8080")
                .defaultHeader("X-Title", "Career Copilot")
                .build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, userPrompt, defaultModel);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, String modelName) {
        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("choices")) {
                List<?> choices = (List<?>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");
                    if (message != null && message.containsKey("content")) {
                        return (String) message.get("content");
                    }
                }
            }
            throw new RuntimeException("Malformed response from OpenRouter: " + response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call OpenRouter", e);
        }
    }
}
