package com.careercopilot.matching;

import java.util.Map;

/**
 * Result of scoring a job against a candidate profile.
 *
 * @param score               overall match score from 0–100
 * @param eligible            true if the candidate passed all hard pre-filters
 * @param ineligibilityReason human-readable reason when {@code eligible} is false; null otherwise
 * @param breakdown           sub-scores by dimension (e.g. {"embedding": 45, "keyword": 30})
 */
public record MatchResult(
        int score,
        boolean eligible,
        String ineligibilityReason,
        Map<String, Integer> breakdown
) {
    public MatchResult {
        breakdown = breakdown == null ? Map.of() : Map.copyOf(breakdown);
    }
}
