package com.careercopilot.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GreenhouseResponse(
        List<GreenhouseJob> jobs
) {}
