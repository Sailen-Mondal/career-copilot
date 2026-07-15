package com.careercopilot.discovery;

import java.time.Instant;
import java.util.Set;

/**
 * Source-agnostic DTO produced by every SourceDiscoveryAdapter.
 * Normalized into JobEntity by JobDiscoveryService.
 */
public record RawJobListing(
        String source,         // e.g. "greenhouse", "lever", "remotive"
        String externalId,     // platform's own ID
        String companySlug,    // board token / company slug
        String companyName,    // human-readable company name
        String title,
        String location,
        String url,
        String descriptionHtml,
        boolean remote,
        Instant postedAt       // nullable — best-effort
) {}
