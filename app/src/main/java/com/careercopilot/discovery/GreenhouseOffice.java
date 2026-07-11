package com.careercopilot.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GreenhouseOffice(
        Long id,
        String name,
        String location
) {}
