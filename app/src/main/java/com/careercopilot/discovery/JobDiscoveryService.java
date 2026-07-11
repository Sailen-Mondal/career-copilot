package com.careercopilot.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class JobDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(JobDiscoveryService.class);

    private final GreenhouseClient greenhouseClient;
    private final EmbeddingClient embeddingClient;
    private final JobRepository jobRepository;

    public JobDiscoveryService(
            GreenhouseClient greenhouseClient,
            EmbeddingClient embeddingClient,
            JobRepository jobRepository) {
        this.greenhouseClient = greenhouseClient;
        this.embeddingClient = embeddingClient;
        this.jobRepository = jobRepository;
    }

    public List<Job> syncBoard(String boardToken) {
        log.info("Starting Greenhouse sync for board: {}", boardToken);
        List<GreenhouseJob> gJobs = greenhouseClient.fetchJobs(boardToken);
        log.info("Fetched {} jobs for board: {}", gJobs.size(), boardToken);

        List<Job> syncedJobs = new ArrayList<>();

        for (GreenhouseJob gJob : gJobs) {
            try {
                // 1. Determine location
                String location = "unknown";
                if (gJob.offices() != null && !gJob.offices().isEmpty()) {
                    location = gJob.offices().get(0).name(); // or location() if name is empty
                    if (location == null || location.isBlank()) {
                        location = gJob.offices().get(0).location();
                    }
                }
                if (location == null || location.isBlank()) {
                    location = "unknown";
                }

                // 2. Generate dedup key
                String dedupKey = JobNormalizer.generateDedupKey(boardToken, gJob.title(), location);

                // 3. Check composite key duplication
                Optional<JobEntity> existingKeyMatch = jobRepository.findByDedupKey(dedupKey);
                if (existingKeyMatch.isPresent()) {
                    log.info("Job skipped (duplicate dedupKey: {}): {}", dedupKey, gJob.title());
                    continue;
                }

                // 4. Strip HTML content
                String descriptionClean = HTMLStripper.stripHtml(gJob.content());

                // 5. Generate embedding vector
                float[] embedding = embeddingClient.getEmbedding(descriptionClean);

                // 6. Check embedding similarity duplication (> 0.98)
                String embeddingStr = JobEntity.serializeVectorToString(embedding);
                Optional<JobEntity> semanticMatch = jobRepository.findNearestSemanticMatch(embeddingStr, 0.98);
                if (semanticMatch.isPresent()) {
                    log.info("Job skipped (semantic duplicate with similarity > 0.98): {}", gJob.title());
                    continue;
                }

                // 7. Parse metadata for sponsorship (optional helper)
                boolean sponsorship = false;
                if (gJob.metadata() != null) {
                    sponsorship = gJob.metadata().stream()
                            .filter(m -> m.name() != null && m.name().toLowerCase().contains("sponsorship"))
                            .anyMatch(m -> m.value() != null && m.value().toLowerCase().contains("yes"));
                }

                // 8. Determine postedAt timestamp
                Instant postedAt = Instant.now();
                if (gJob.updatedAt() != null) {
                    try {
                        postedAt = Instant.parse(gJob.updatedAt());
                    } catch (Exception e) {
                        log.warn("Failed to parse updatedAt: {}, using now", gJob.updatedAt());
                    }
                }

                // 9. Generate deterministic UUID
                UUID jobId = UUID.nameUUIDFromBytes(("greenhouse:" + boardToken + ":" + gJob.id()).getBytes());

                // Create domain Job
                Job job = new Job(
                        jobId,
                        "greenhouse",
                        String.valueOf(gJob.id()),
                        gJob.absoluteUrl() != null ? URI.create(gJob.absoluteUrl()) : URI.create("https://boards.greenhouse.io/" + boardToken),
                        boardToken, // Board token is the company identifier
                        gJob.title(),
                        location,
                        location.toLowerCase().contains("remote") ? "remote" : "onsite",
                        gJob.content(),
                        descriptionClean,
                        java.util.Set.of(),
                        null,
                        null,
                        null,
                        sponsorship,
                        postedAt,
                        Instant.now(),
                        Instant.now(),
                        JobStatus.ACTIVE,
                        dedupKey,
                        embedding
                );

                // Save to database
                JobEntity entity = new JobEntity(job);
                JobEntity saved = jobRepository.save(entity);
                syncedJobs.add(saved.toDomain());
                log.info("Successfully synced job: {} (UUID: {})", job.title(), jobId);

            } catch (Exception e) {
                log.error("Error syncing Greenhouse job ID: " + gJob.id(), e);
            }
        }

        return syncedJobs;
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll().stream()
                .map(JobEntity::toDomain)
                .collect(Collectors.toList());
    }
}
