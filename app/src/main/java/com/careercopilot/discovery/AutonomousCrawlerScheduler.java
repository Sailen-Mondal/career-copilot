package com.careercopilot.discovery;

import com.careercopilot.applications.ApplicationWorkflowService;
import com.careercopilot.automation.KillSwitchService;
import com.careercopilot.profile.MasterProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Heart of the autonomous operation.
 *
 * <p>On a configurable schedule, this scheduler:
 * <ol>
 *   <li>Checks the kill switch (dashboard Emergency Stop)</li>
 *   <li>Checks the daily application cap</li>
 *   <li>Calls every {@link SourceDiscoveryAdapter} to fetch fresh job listings</li>
 *   <li>For each new job, runs the full application workflow:
 *       score → generate resume/cover letter → queue for Playwright submission</li>
 * </ol>
 */
@Component
@EnableScheduling
public class AutonomousCrawlerScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutonomousCrawlerScheduler.class);

    private final List<SourceDiscoveryAdapter> adapters;
    private final JobDiscoveryService jobDiscoveryService;
    private final ApplicationWorkflowService applicationWorkflowService;
    private final KillSwitchService killSwitchService;
    private final MasterProfileRepository masterProfileRepository;

    @Value("${automation.daily-cap:20}")
    private int dailyCap;

    @Value("${automation.autonomy-threshold:30}")
    private int autonomyThreshold;

    public AutonomousCrawlerScheduler(
            List<SourceDiscoveryAdapter> adapters,
            JobDiscoveryService jobDiscoveryService,
            ApplicationWorkflowService applicationWorkflowService,
            KillSwitchService killSwitchService,
            MasterProfileRepository masterProfileRepository) {
        this.adapters = adapters;
        this.jobDiscoveryService = jobDiscoveryService;
        this.applicationWorkflowService = applicationWorkflowService;
        this.killSwitchService = killSwitchService;
        this.masterProfileRepository = masterProfileRepository;
    }

    /**
     * Main crawl loop — runs every 30 minutes, with 15-second startup delay.
     * Initial delay gives Spring Boot time to fully initialise all beans.
     */
    @Scheduled(initialDelayString = "${automation.crawler.startup-delay-ms:15000}",
               fixedDelayString  = "${automation.crawler.interval-ms:1800000}")
    public void crawlAndApply() {
        log.info("=== [Crawler] Autonomous crawl cycle starting ===");

        // 1. Kill-switch check
        if (killSwitchService.isHalted()) {
            log.info("[Crawler] Kill switch ACTIVE — skipping crawl cycle.");
            return;
        }

        // 2. Find the first active profile
        var profiles = masterProfileRepository.findAll();
        if (profiles.isEmpty()) {
            log.warn("[Crawler] No profiles found — skipping crawl.");
            return;
        }
        UUID profileId = profiles.get(0).getId();
        log.info("[Crawler] Using profileId={}", profileId);

        // 3. Track daily submission count against cap
        AtomicInteger submitted = new AtomicInteger(0);

        // 4. Run every adapter
        for (SourceDiscoveryAdapter adapter : adapters) {
            if (killSwitchService.isHalted()) {
                log.info("[Crawler] Kill switch activated mid-cycle — halting.");
                break;
            }
            if (submitted.get() >= dailyCap) {
                log.info("[Crawler] Daily cap of {} reached — stopping.", dailyCap);
                break;
            }

            log.info("[Crawler] Running adapter: {}", adapter.sourceName());
            try {
                List<RawJobListing> raw = adapter.fetchJobs();
                log.info("[Crawler] {} returned {} raw listings", adapter.sourceName(), raw.size());

                List<Job> newJobs = jobDiscoveryService.ingestRawListings(raw);
                log.info("[Crawler] {} new unique jobs ingested from {}", newJobs.size(), adapter.sourceName());

                for (Job job : newJobs) {
                    if (killSwitchService.isHalted() || submitted.get() >= dailyCap) break;
                    try {
                        applicationWorkflowService.runWorkflow(job.id(), profileId);
                        submitted.incrementAndGet();
                        log.info("[Crawler] Queued application for job='{}' (total={})",
                                job.title(), submitted.get());
                    } catch (Exception e) {
                        log.warn("[Crawler] Workflow failed for job='{}': {}", job.title(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[Crawler] Adapter '{}' threw unexpected error: {}", adapter.sourceName(), e.getMessage(), e);
            }
        }

        log.info("=== [Crawler] Crawl cycle complete — {} applications queued ===", submitted.get());
    }
}
