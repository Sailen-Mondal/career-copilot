package com.careercopilot.shared;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Production LLM client that delegates to Vertex AI Gemini via Spring AI's {@link ChatClient}.
 *
 * <p>Activated only when {@code app.ai.client-type=vertex}. In dev/CI the {@link MockLlmClient}
 * is used instead.
 */
@Service
@ConditionalOnProperty(name = "app.ai.client-type", havingValue = "vertex")
public class VertexAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public VertexAiLlmClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, String modelName) {
        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions.builder()
                        .withModel(modelName)
                        .build())
                .call()
                .content();
    }
}
