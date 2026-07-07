package com.careercopilot.generation;

import java.util.List;

public record GroundednessReport(
        boolean passed,
        List<String> issues
) {
    public GroundednessReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
