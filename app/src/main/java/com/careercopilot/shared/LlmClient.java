package com.careercopilot.shared;

/**
 * Generates text from a system prompt and user prompt.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link MockLlmClient} — deterministic, no network; used in dev/CI</li>
 *   <li>{@link VertexAiLlmClient} — delegates to Vertex AI Gemini via Spring AI; used in prod</li>
 * </ul>
 */
public interface LlmClient {

    /**
     * Generates a text response given a system instruction and a user message.
     *
     * @param systemPrompt the system-level instruction to steer the model
     * @param userPrompt   the user-level content/input
     * @return the generated text content
     */
    String generate(String systemPrompt, String userPrompt);

    /**
     * Generates a text response given a system instruction, user message, and specific model name.
     *
     * @param systemPrompt the system-level instruction to steer the model
     * @param userPrompt   the user-level content/input
     * @param modelName    the specific AI model name to use
     * @return the generated text content
     */
    String generate(String systemPrompt, String userPrompt, String modelName);
}
