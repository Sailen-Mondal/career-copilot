package com.careercopilot.generation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GeneratedDocument(
        UUID id,
        UUID applicationId,
        DocumentType type,
        String content,
        Instant generatedAt,
        List<UUID> sourceFactIds
) {
    public GeneratedDocument {
        if (id == null) {
            throw new IllegalArgumentException("GeneratedDocument id is required.");
        }
        if (type == null) {
            throw new IllegalArgumentException("GeneratedDocument type is required.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("GeneratedDocument content is required.");
        }
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        sourceFactIds = sourceFactIds == null ? List.of() : List.copyOf(sourceFactIds);
    }
}
