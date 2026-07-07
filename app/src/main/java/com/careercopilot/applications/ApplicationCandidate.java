package com.careercopilot.applications;

import com.careercopilot.discovery.Job;

public record ApplicationCandidate(
        Job job,
        int matchScore,
        boolean groundednessPassed,
        boolean hasUnsupportedCustomFields,
        boolean circuitBreakerOpen,
        int submittedToday
) {
    public ApplicationCandidate {
        if (job == null) {
            throw new IllegalArgumentException("Application candidate job is required.");
        }
        if (matchScore < 0 || matchScore > 100) {
            throw new IllegalArgumentException("Match score must be between 0 and 100.");
        }
        if (submittedToday < 0) {
            throw new IllegalArgumentException("Submitted count cannot be negative.");
        }
    }
}
