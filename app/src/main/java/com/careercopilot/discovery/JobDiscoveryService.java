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
                Optional<JobEntity> semanticMatch = jobRepository.findNearestSemanticMatch(embedding, 0.98);
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

                // Extract required skills
                java.util.Set<String> skillsRequired = new java.util.HashSet<>();
                String textForSkills = (gJob.title() + " " + descriptionClean).toLowerCase();
                java.util.List<String> commonSkills = java.util.List.of("java", "spring boot", "postgresql", "redis", "react", "docker", "kubernetes", "aws", "sql", "gcp", "python", "javascript", "typescript", "c++", "golang");
                for (String skill : commonSkills) {
                    if (textForSkills.contains(skill)) {
                        String normalizedSkill = switch (skill) {
                            case "spring boot" -> "Spring Boot";
                            case "postgresql" -> "PostgreSQL";
                            case "redis" -> "Redis";
                            case "react" -> "React";
                            case "docker" -> "Docker";
                            case "kubernetes" -> "Kubernetes";
                            case "aws" -> "AWS";
                            case "sql" -> "SQL";
                            case "gcp" -> "GCP";
                            case "python" -> "Python";
                            case "javascript" -> "JavaScript";
                            case "typescript" -> "TypeScript";
                            case "c++" -> "C++";
                            case "golang" -> "Go";
                            default -> skill.substring(0, 1).toUpperCase() + skill.substring(1);
                        };
                        skillsRequired.add(normalizedSkill);
                    }
                }

                // Extract seniority
                String titleLower = gJob.title().toLowerCase();
                String seniority = "MID";
                if (titleLower.contains("senior") || titleLower.contains("sr") || titleLower.contains("lead") || titleLower.contains("staff") || titleLower.contains("principal") || titleLower.contains(" ii") || titleLower.contains(" iii") || titleLower.contains(" iv") || titleLower.contains(" v") || titleLower.contains("l3") || titleLower.contains("l4") || titleLower.contains("l5") || titleLower.contains("l6")) {
                    seniority = "SENIOR";
                } else if (titleLower.contains("junior") || titleLower.contains("jr") || titleLower.contains("entry") || titleLower.contains("associate") || titleLower.contains("intern") || titleLower.contains(" i")) {
                    seniority = "JUNIOR";
                }

                // Extract work authorization required
                String descLower = descriptionClean.toLowerCase();
                String workAuthRequired = null;
                if (descLower.contains("us citizen") || descLower.contains("u.s. citizen") || descLower.contains("citizenship required") || descLower.contains("clearance required") || descLower.contains("must be a citizen")) {
                    workAuthRequired = "US_CITIZEN";
                } else if (descLower.contains("green card") || descLower.contains("permanent resident")) {
                    workAuthRequired = "GREEN_CARD";
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
                        skillsRequired,
                        seniority,
                        null,
                        workAuthRequired,
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

    /**
     * Ingests a batch of source-agnostic {@link RawJobListing} objects.
     * Applies the same dedup + embedding + normalisation pipeline as syncBoard().
     * Returns only newly-inserted jobs (skips duplicates).
     */
    public List<Job> ingestRawListings(List<RawJobListing> rawListings) {
        List<Job> newJobs = new ArrayList<>();
        for (RawJobListing raw : rawListings) {
            try {
                // 1. Dedup key
                String dedupKey = JobNormalizer.generateDedupKey(
                        raw.companySlug(), raw.title(), raw.location());

                if (jobRepository.findByDedupKey(dedupKey).isPresent()) {
                    log.debug("[ingest] Duplicate dedupKey '{}' — skipping '{}'", dedupKey, raw.title());
                    continue;
                }

                // 2. Strip HTML
                String descClean = HTMLStripper.stripHtml(
                        raw.descriptionHtml() != null ? raw.descriptionHtml() : "");

                // 3. Embedding & semantic dedup
                float[] embedding = embeddingClient.getEmbedding(descClean);
                if (jobRepository.findNearestSemanticMatch(embedding, 0.98).isPresent()) {
                    log.debug("[ingest] Semantic duplicate for '{}' — skipping", raw.title());
                    continue;
                }

                // 4. Skill extraction
                java.util.Set<String> skills = new java.util.HashSet<>();
                String textForSkills = (raw.title() + " " + descClean).toLowerCase();
                java.util.List<String> commonSkills = java.util.List.of(
                        "java", "spring boot", "postgresql", "redis", "react", "docker",
                        "kubernetes", "aws", "sql", "gcp", "python", "javascript",
                        "typescript", "c++", "golang");
                for (String skill : commonSkills) {
                    if (textForSkills.contains(skill)) {
                        skills.add(skill.substring(0, 1).toUpperCase() + skill.substring(1));
                    }
                }

                // 5. Seniority
                String titleLower = raw.title().toLowerCase();
                String seniority = "MID";
                if (titleLower.contains("senior") || titleLower.contains("sr")
                        || titleLower.contains("lead") || titleLower.contains("staff")
                        || titleLower.contains("principal")) {
                    seniority = "SENIOR";
                } else if (titleLower.contains("junior") || titleLower.contains("jr")
                        || titleLower.contains("entry") || titleLower.contains("intern")) {
                    seniority = "JUNIOR";
                }

                // 6. Work-auth detection
                String descLower = descClean.toLowerCase();
                String workAuth = null;
                if (descLower.contains("us citizen") || descLower.contains("clearance required")) {
                    workAuth = "US_CITIZEN";
                } else if (descLower.contains("green card") || descLower.contains("permanent resident")) {
                    workAuth = "GREEN_CARD";
                }

                // 7. Deterministic UUID
                UUID jobId = UUID.nameUUIDFromBytes(
                        (raw.source() + ":" + raw.companySlug() + ":" + raw.externalId()).getBytes());

                java.net.URI jobUrl;
                try { jobUrl = java.net.URI.create(raw.url()); } catch (Exception e) {
                    jobUrl = java.net.URI.create("https://example.com");
                }

                Job job = new Job(
                        jobId,
                        raw.source(),
                        raw.externalId(),
                        jobUrl,
                        raw.companyName() != null ? raw.companyName() : raw.companySlug(),
                        raw.title(),
                        raw.location(),
                        raw.remote() ? "remote" : "onsite",
                        raw.descriptionHtml(),
                        descClean,
                        skills,
                        seniority,
                        null,
                        workAuth,
                        false,
                        raw.postedAt() != null ? raw.postedAt() : Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        JobStatus.ACTIVE,
                        dedupKey,
                        embedding
                );

                JobEntity entity = new JobEntity(job);
                jobRepository.save(entity);
                newJobs.add(job);
                log.info("[ingest] Saved new job: '{}' from {} (id={})",
                        job.title(), raw.source(), jobId);

            } catch (Exception e) {
                log.error("[ingest] Error processing raw listing '{}' from {}: {}",
                        raw.title(), raw.source(), e.getMessage());
            }
        }
        return newJobs;
    }
}
