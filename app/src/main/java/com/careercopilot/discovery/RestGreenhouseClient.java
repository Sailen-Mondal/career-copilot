package com.careercopilot.discovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.discovery.greenhouse.use-mock", havingValue = "false")
public class RestGreenhouseClient implements GreenhouseClient {

    private final RestClient restClient;

    public RestGreenhouseClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://boards-api.greenhouse.io/v1").build();
    }

    @Override
    public List<GreenhouseJob> fetchJobs(String boardToken) {
        GreenhouseResponse response = restClient.get()
                .uri("/boards/{boardToken}/jobs?content=true", boardToken)
                .retrieve()
                .body(GreenhouseResponse.class);

        return response != null && response.jobs() != null ? response.jobs() : List.of();
    }
}
