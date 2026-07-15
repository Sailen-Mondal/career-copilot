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
 * Fetches remote tech jobs from the Himalayas public API.
 * URL: https://himalayas.app/jobs/api
 * No authentication required.
 */
@Component
public class HimalayasDiscoveryAdapter implements SourceDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(HimalayasDiscoveryAdapter.class);

    private final RestClient restClient;

    public HimalayasDiscoveryAdapter(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://himalayas.app").build();
    }

    @Override
    public String sourceName() {
        return "himalayas";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawJobListing> fetchJobs() {
        List<RawJobListing> all = new ArrayList<>();
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/jobs/api?limit=50")
                    .retrieve()
                    .body(Map.class);

            if (response == null) return all;
            List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.get("jobs");
            if (jobs == null) return all;

            for (Map<String, Object> j : jobs) {
                try {
                    String id = String.valueOf(j.get("id"));
                    String title = String.valueOf(j.getOrDefault("title", ""));
                    String company = String.valueOf(j.getOrDefault("companyName", ""));
                    String url = String.valueOf(j.getOrDefault("applicationUrl", j.getOrDefault("url", "")));
                    String desc = String.valueOf(j.getOrDefault("description", ""));
                    String pubDate = String.valueOf(j.getOrDefault("createdAt", ""));
                    Instant posted = null;
                    try { posted = Instant.parse(pubDate); } catch (Exception ignored) {}

                    all.add(new RawJobListing(
                            "himalayas", id, company.toLowerCase(), company,
                            title, "Remote", url, desc, true, posted
                    ));
                } catch (Exception ex) {
                    log.warn("[himalayas] Skipping malformed job: {}", ex.getMessage());
                }
            }
            log.info("[himalayas] Fetched {} jobs", jobs.size());
        } catch (Exception e) {
            log.warn("[himalayas] Failed to fetch: {}", e.getMessage());
        }
        return all;
    }
}
