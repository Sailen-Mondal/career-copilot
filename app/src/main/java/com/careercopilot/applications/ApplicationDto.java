package com.careercopilot.applications;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApplicationDto(
        UUID id,
        UUID jobId,
        String company,
        String title,
        String url,
        UUID profileId,
        UUID resumeVersionId,
        UUID coverLetterVersionId,
        int matchScore,
        Map<String, Integer> matchScoreBreakdown,
        String automationTier,
        String status,
        Instant submittedAt,
        String platform,
        String externalApplicationId,
        boolean groundednessCheckPassed,
        Map<String, Object> groundednessReport,
        List<String> auditTrail,
        String reason,
        String resumeContent,
        String coverLetterContent
) {}
