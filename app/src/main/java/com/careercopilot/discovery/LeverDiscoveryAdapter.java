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
 * Fetches jobs from the Lever public postings API.
 * URL: https://api.lever.co/v0/postings/{company}?mode=json
 * No authentication required.
 */
@Component
public class LeverDiscoveryAdapter implements SourceDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(LeverDiscoveryAdapter.class);
    private static final String BASE_URL = "https://api.lever.co/v0";

    /* Well-known Lever boards to crawl */
    private static final List<String> BOARDS = List.of(
            "github", "discord", "linear", "retool", "grafana",
            "vercel", "planetscale", "posthog", "supabase", "fly",
            "clerk", "neon", "turso", "modal", "render", "bolster"
    );

    private final RestClient restClient;

    public LeverDiscoveryAdapter(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public String sourceName() {
        return "lever";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawJobListing> fetchJobs() {
        List<RawJobListing> all = new ArrayList<>();
        for (String company : BOARDS) {
            try {
                List<Map<String, Object>> postings = restClient.get()
                        .uri("/postings/{company}?mode=json", company)
                        .retrieve()
                        .body(List.class);

                if (postings == null) continue;

                for (Map<String, Object> p : postings) {
                    try {
                        String id   = String.valueOf(p.get("id"));
                        String text = String.valueOf(p.getOrDefault("text", ""));
                        String hostedUrl = String.valueOf(p.getOrDefault("hostedUrl", ""));

                        Map<String, Object> cats = (Map<String, Object>) p.get("categories");
                        String location = cats != null ? String.valueOf(cats.getOrDefault("location", "Remote")) : "Remote";
                        boolean remote  = location.toLowerCase().contains("remote");

                        Map<String, Object> desc = (Map<String, Object>) p.get("descriptionPlain");
                        String descText = desc != null ? String.valueOf(desc) : String.valueOf(p.getOrDefault("description", ""));

                        Long createdAt = p.get("createdAt") instanceof Number n ? n.longValue() : null;
                        Instant posted = createdAt != null ? Instant.ofEpochMilli(createdAt) : null;

                        all.add(new RawJobListing(
                                "lever", id, company, company,
                                text, location, hostedUrl, descText, remote, posted
                        ));
                    } catch (Exception ex) {
                        log.warn("[lever] Skipping malformed posting for company={}: {}", company, ex.getMessage());
                    }
                }
                log.info("[lever] {} fetched {} jobs", company, postings.size());
            } catch (Exception e) {
                log.warn("[lever] Failed to fetch board '{}': {}", company, e.getMessage());
            }
        }
        return all;
    }
}
