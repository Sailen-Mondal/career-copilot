package com.careercopilot.automation;

import java.util.List;

/**
 * Result payload consumed from the Redis {@code cc:automation:results} stream.
 * Published by the TypeScript Playwright worker after form filling.
 */
public record AutomationResult(
        String applicationId,
        String status,              // shadow_completed | submitted | skipped | failed
        List<String> fieldsFilled,
        List<String> unsupportedFields,
        String screenshotPath,
        String platformResponse
) {}
