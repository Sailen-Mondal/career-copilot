package com.careercopilot.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpenRouterLlmClient Unit Test")
class OpenRouterLlmClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private OpenRouterLlmClient openRouterLlmClient;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.defaultHeader(anyString(), any(String[].class))).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        openRouterLlmClient = new OpenRouterLlmClient(
                restClientBuilder,
                "mock-api-key",
                "openrouter/free",
                "https://openrouter.ai/api/v1"
        );
    }

    @Test
    @DisplayName("should call OpenRouter and return generated text response")
    @SuppressWarnings("unchecked")
    void generate_shouldCallOpenRouterAndReturnResponse() {
        // Arrange
        String systemPrompt = "You are a helpful assistant.";
        String userPrompt = "Hello!";
        String expectedResponseText = "Hi there! How can I help you today?";

        Map<String, Object> mockResponseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", expectedResponseText))
                )
        );

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/chat/completions"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(mockResponseBody);

        // Act
        String actualResponse = openRouterLlmClient.generate(systemPrompt, userPrompt);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponseText);
        verify(restClientBuilder).baseUrl("https://openrouter.ai/api/v1");
        verify(restClientBuilder).defaultHeader("Authorization", "Bearer mock-api-key");
    }
}
