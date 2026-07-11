package com.careercopilot.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GreenhouseMetadata(
        Long id,
        String name,
        String value
) {}
