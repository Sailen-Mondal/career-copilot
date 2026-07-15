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
 * Fetches software engineering jobs from the Arbeitnow public API.
 * URL: https://www.arbeitnow.com/api/job-board-api
 * No authentication required.
 */
@Component
public class ArbeitnowDiscoveryAdapter implements SourceDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowDiscoveryAdapter.class);

    private final RestClient restClient;

    public ArbeitnowDiscoveryAdapter(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://www.arbeitnow.com/api").build();
    }

    @Override
    public String sourceName() {
        return "arbeitnow";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawJobListing> fetchJobs() {
        List<RawJobListing> all = new ArrayList<>();
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/job-board-api")
                    .retrieve()
                    .body(Map.class);

            if (response == null) return all;
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null) return all;

            for (Map<String, Object> j : data) {
                try {
                    String slug = String.valueOf(j.get("slug"));
                    String title = String.valueOf(j.getOrDefault("title", ""));
                    String company = String.valueOf(j.getOrDefault("company_name", ""));
                    String url = String.valueOf(j.getOrDefault("url", ""));
                    String desc = String.valueOf(j.getOrDefault("description", ""));
                    String location = String.valueOf(j.getOrDefault("location", "Remote"));
                    boolean remote = Boolean.parseBoolean(String.valueOf(j.getOrDefault("remote", "false")));

                    Object createdAt = j.get("created_at");
                    Instant posted = null;
                    if (createdAt instanceof Number n) {
                        posted = Instant.ofEpochSecond(n.longValue());
                    }

                    all.add(new RawJobListing(
                            "arbeitnow", slug, company.toLowerCase(), company,
                            title, location, url, desc, remote, posted
                    ));
                } catch (Exception ex) {
                    log.warn("[arbeitnow] Skipping malformed job: {}", ex.getMessage());
                }
            }
            log.info("[arbeitnow] Fetched {} jobs", data.size());
        } catch (Exception e) {
            log.warn("[arbeitnow] Failed to fetch: {}", e.getMessage());
        }
        return all;
    }
}
