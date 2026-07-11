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
}
