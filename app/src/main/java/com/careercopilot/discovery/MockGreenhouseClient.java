package com.careercopilot.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.discovery.greenhouse.use-mock", havingValue = "true", matchIfMissing = true)
public class MockGreenhouseClient implements GreenhouseClient {

    private final ObjectMapper objectMapper;
    private final Resource mockFeedResource;

    public MockGreenhouseClient(
            ObjectMapper objectMapper,
            @Value("classpath:db/mock-greenhouse-feed.json") Resource mockFeedResource) {
        this.objectMapper = objectMapper;
        this.mockFeedResource = mockFeedResource;
    }

    @Override
    public List<GreenhouseJob> fetchJobs(String boardToken) {
        try (InputStream is = mockFeedResource.getInputStream()) {
            GreenhouseResponse response = objectMapper.readValue(is, GreenhouseResponse.class);
            return response != null && response.jobs() != null ? response.jobs() : List.of();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read mock greenhouse feed", e);
        }
    }
}
