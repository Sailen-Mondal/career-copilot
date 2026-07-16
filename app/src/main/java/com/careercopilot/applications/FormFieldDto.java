package com.careercopilot.applications;

import java.util.List;

/**
 * Data transfer object representing a form field extracted by the Playwright worker.
 */
public record FormFieldDto(
        String selector,
        String identifier,
        String labelText,
        String type,
        List<String> options,
        boolean required
) {}
