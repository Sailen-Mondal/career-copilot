package com.careercopilot.generation;

import java.util.List;

/**
 * Thrown when the {@link GroundednessVerifier} detects hallucinations or
 * unverified claims in a {@link GeneratedDocument}.
 */
public class GroundednessException extends RuntimeException {

    private final List<String> issues;

    public GroundednessException(List<String> issues) {
        super("Groundedness check failed: " + issues);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** Returns the list of specific groundedness violations detected. */
    public List<String> getIssues() {
        return issues;
    }
}
