package com.careercopilot.discovery;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

public record Job(
        UUID id,
        String source,
        String externalId,
        URI url,
        String company,
        String title,
        Set<String> requiredSkills,
        boolean sponsorshipAvailable,
        JobStatus status
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
