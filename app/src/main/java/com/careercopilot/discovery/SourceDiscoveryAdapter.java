package com.careercopilot.discovery;

import java.util.List;

/**
 * Common interface for all job-board adapters.
 * Each adapter is responsible for one source (Greenhouse, Lever, Remotive…).
 */
public interface SourceDiscoveryAdapter {

    /** Human-readable source identifier stored in JobEntity.source. */
    String sourceName();

    /**
     * Fetch raw job listings from the source.
     * Implementations must be tolerant of transient HTTP failures
     * and return an empty list rather than throwing.
     */
    List<RawJobListing> fetchJobs();
}
