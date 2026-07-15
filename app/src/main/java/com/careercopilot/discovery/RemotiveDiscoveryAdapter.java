package com.careercopilot.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches remote software engineering jobs from the Remotive public API.
 * URL: https://remotive.com/api/remote-jobs?category=software-dev&limit=50
 * No authentication required.
 */
@Component
public class RemotiveDiscoveryAdapter implements SourceDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(RemotiveDiscoveryAdapter.class);

    private final RestClient restClient;

    public RemotiveDiscoveryAdapter(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://remotive.com/api").build();
    }

    @Override
    public String sourceName() {
        return "remotive";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawJobListing> fetchJobs() {
        List<RawJobListing> all = new ArrayList<>();
        List<String> categories = List.of("software-dev", "devops-sysadmin", "data");

        for (String category : categories) {
            try {
                Map<String, Object> response = restClient.get()
                        .uri("/remote-jobs?category={cat}&limit=50", category)
                        .retrieve()
                        .body(Map.class);

                if (response == null) continue;
                List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.get("jobs");
                if (jobs == null) continue;

                for (Map<String, Object> j : jobs) {
                    try {
                        String id = String.valueOf(j.get("id"));
                        String title = String.valueOf(j.getOrDefault("title", ""));
                        String company = String.valueOf(j.getOrDefault("company_name", ""));
                        String url = String.valueOf(j.getOrDefault("url", ""));
                        String desc = String.valueOf(j.getOrDefault("description", ""));
                        String pubDate = String.valueOf(j.getOrDefault("publication_date", ""));
                        Instant posted = null;
                        try { posted = Instant.parse(pubDate); } catch (Exception ignored) {}

                        all.add(new RawJobListing(
                                "remotive", id, company.toLowerCase(), company,
                                title, "Remote", url, desc, true, posted
                        ));
                    } catch (Exception ex) {
                        log.warn("[remotive] Skipping malformed job: {}", ex.getMessage());
                    }
                }
                log.info("[remotive] category='{}' fetched {} jobs", category, jobs.size());
            } catch (Exception e) {
                log.warn("[remotive] Failed to fetch category '{}': {}", category, e.getMessage());
            }
        }
        return all;
    }
}
