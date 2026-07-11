package com.careercopilot.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GreenhouseJob(
        Long id,
        String title,
        @JsonProperty("absolute_url") String absoluteUrl,
        @JsonProperty("updated_at") String updatedAt,
        String content,
        List<GreenhouseOffice> offices,
        List<GreenhouseMetadata> metadata
) {}
