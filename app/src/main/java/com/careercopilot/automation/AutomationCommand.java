package com.careercopilot.automation;

/**
 * Payload published to the Redis {@code cc:automation:jobs} stream.
 * Consumed by the TypeScript Playwright worker.
 */
public record AutomationCommand(
        String applicationId,
        String jobUrl,
        String mode,                // "shadow" or "live"
        String profileSnapshotId,
        String resumeDocumentId,
        String coverLetterDocumentId
) {}
