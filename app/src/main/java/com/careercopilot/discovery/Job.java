package com.careercopilot.discovery;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Job(
        UUID id,
        String source,
        String externalId,
        URI url,
        String company,
        String title,
        String location,
        String locationType,
        String descriptionRaw,
        String descriptionClean,
        Set<String> requiredSkills,
        String seniority,
        SalaryRange salaryRange,
        String workAuthRequired,
        boolean sponsorshipAvailable,
        Instant postedAt,
        Instant scrapedAt,
        Instant lastVerifiedAt,
        JobStatus status,
        String dedupKey,
        float[] embeddingVector
) {
    public Job {
        if (id == null) {
            throw new IllegalArgumentException("Job id is required.");
        }
        if (company == null || company.isBlank()) {
            throw new IllegalArgumentException("Job company is required.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Job title is required.");
        }
        requiredSkills = requiredSkills == null ? Set.of() : Set.copyOf(requiredSkills);
        status = status == null ? JobStatus.ACTIVE : status;
    }

    public boolean isOpen() {
        return status == JobStatus.ACTIVE;
    }
}
