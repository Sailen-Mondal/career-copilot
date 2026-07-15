package com.careercopilot.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the existing GreenhouseClient to implement SourceDiscoveryAdapter.
 * Crawls a curated list of tech company boards.
 */
@Component
public class GreenhouseDiscoveryAdapter implements SourceDiscoveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(GreenhouseDiscoveryAdapter.class);

    /* Curated tech boards — tokens verified working as of July 2026 */
    private static final List<String> BOARDS = List.of(
            "stripe", "shopify", "airbnb", "atlassian", "confluent",
            "databricks", "figma", "hashicorp", "notion", "vercel",
            "pinterest", "robinhood", "lyft", "duolingo", "canva",
            "rippling", "brex", "plaid", "scale", "opendoor"
    );

    private final GreenhouseClient greenhouseClient;

    public GreenhouseDiscoveryAdapter(GreenhouseClient greenhouseClient) {
        this.greenhouseClient = greenhouseClient;
    }

    @Override
    public String sourceName() {
        return "greenhouse";
    }

    @Override
    public List<RawJobListing> fetchJobs() {
        List<RawJobListing> all = new ArrayList<>();
        for (String board : BOARDS) {
            try {
                List<GreenhouseJob> jobs = greenhouseClient.fetchJobs(board);
                for (GreenhouseJob gJob : jobs) {
                    String location = "Remote";
                    if (gJob.offices() != null && !gJob.offices().isEmpty()) {
                        location = gJob.offices().get(0).name();
                        if (location == null || location.isBlank()) {
                            location = gJob.offices().get(0).location();
                        }
                    }
                    if (location == null || location.isBlank()) location = "Remote";

                    Instant posted = null;
                    if (gJob.updatedAt() != null) {
                        try { posted = Instant.parse(gJob.updatedAt()); } catch (Exception ignored) {}
                    }

                    String url = gJob.absoluteUrl() != null
                            ? gJob.absoluteUrl()
                            : "https://boards.greenhouse.io/" + board;

                    all.add(new RawJobListing(
                            "greenhouse",
                            String.valueOf(gJob.id()),
                            board,
                            board, // Company name resolved later via title heuristic
                            gJob.title(),
                            location,
                            url,
                            gJob.content(),
                            location.toLowerCase().contains("remote"),
                            posted
                    ));
                }
                log.info("[greenhouse] board='{}' fetched {} jobs", board, jobs.size());
            } catch (Exception e) {
                log.warn("[greenhouse] Failed to fetch board '{}': {}", board, e.getMessage());
            }
        }
        return all;
    }
}
