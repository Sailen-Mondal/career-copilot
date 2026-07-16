package com.careercopilot.shared;

import com.careercopilot.discovery.EmbeddingClient;
import com.careercopilot.discovery.MockEmbeddingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires AI client beans based on the {@code app.ai.client-type} property.
 *
 * <p>When {@code app.ai.client-type=mock} (the default in dev/CI), both
 * {@link MockLlmClient} and {@link MockEmbeddingClient} are registered.
 * The production Vertex AI {@link VertexAiLlmClient} is self-registered via
 * {@code @ConditionalOnProperty} when {@code app.ai.client-type=vertex}.
 */
@Configuration
public class AiClientConfig {

    static {
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.exists(envPath)) {
                java.nio.file.Files.readAllLines(envPath).forEach(line -> {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                });
            }
        } catch (Exception e) {
            // Ignore environment file loading errors
        }
    }

    /**
     * Registers the mock LLM client when client-type is "mock" (default if missing).
     */
    @Bean
    @ConditionalOnProperty(name = "app.ai.client-type", havingValue = "mock", matchIfMissing = true)
    public LlmClient mockLlmClient() {
        return new MockLlmClient();
    }

    /**
     * Registers the mock embedding client when client-type is "mock" (default if missing).
     */
    @Bean
    @ConditionalOnProperty(name = "app.ai.client-type", havingValue = "mock", matchIfMissing = true)
    public EmbeddingClient mockEmbeddingClient() {
        return new MockEmbeddingClient();
    }

    /**
     * Registers a fallback RestClient.Builder bean if not auto-configured.
     */
    @Bean
    public org.springframework.web.client.RestClient.Builder restClientBuilder() {
        return org.springframework.web.client.RestClient.builder();
    }
}
