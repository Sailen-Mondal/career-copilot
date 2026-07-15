package com.careercopilot.discovery;

import com.careercopilot.shared.ApiKeyAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes discovery source metadata and a manual crawl trigger.
 *
 * <p>All endpoints require the X-API-Key header.
 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryController.class);

    private final List<SourceDiscoveryAdapter> adapters;
    private final AutonomousCrawlerScheduler crawlerScheduler;

    public DiscoveryController(List<SourceDiscoveryAdapter> adapters,
                               AutonomousCrawlerScheduler crawlerScheduler) {
        this.adapters = adapters;
        this.crawlerScheduler = crawlerScheduler;
    }

    /**
     * GET /api/discovery/sources
     * Returns the list of registered source adapters and their names.
     */
    @GetMapping("/sources")
    public ResponseEntity<List<Map<String, Object>>> getSources() {
        List<Map<String, Object>> sources = adapters.stream()
                .map(a -> Map.<String, Object>of(
                        "source", a.sourceName(),
                        "enabled", true
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(sources);
    }

    /**
     * POST /api/discovery/trigger
     * Manually kicks off a crawl cycle immediately (does not wait for schedule).
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> trigger() {
        log.info("[DiscoveryController] Manual crawl trigger requested.");
        // Run async so the HTTP call returns immediately
        Thread.ofVirtual().start(crawlerScheduler::crawlAndApply);
        return ResponseEntity.accepted().body(Map.of("status", "crawl started"));
    }
}
