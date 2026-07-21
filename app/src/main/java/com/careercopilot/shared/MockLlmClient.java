package com.careercopilot.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic mock LLM client for use in dev and CI environments.
 *
 * <p>Does not make any network calls. Returns a fixed response that echoes every
 * {@code [fact:<UUID>]} marker found in the {@code userPrompt} so that the
 * {@link com.careercopilot.generation.GroundednessVerifier} passes in tests.
 */
public class MockLlmClient implements LlmClient {

    private static final Pattern FACT_MARKER =
            Pattern.compile("\\[fact:([0-9a-fA-F-]{36})\\]");

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (systemPrompt != null && systemPrompt.contains("recruiter")) {
            return "{\"scoreAdjustment\": 0, \"reasoning\": \"Mock matching completed successfully.\"}";
        }
        if (systemPrompt != null && systemPrompt.contains("form-filling")) {
            Pattern idPattern = Pattern.compile("\"identifier\"\\s*:\\s*\"([^\"]+)\"");
            Matcher idMatcher = idPattern.matcher(userPrompt);
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            while (idMatcher.find()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(idMatcher.group(1)).append("\":\"Mock Answer\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        // Extract all [fact:<UUID>] markers from the user prompt and echo them in output
        List<String> markers = new ArrayList<>();
        if (userPrompt != null) {
            Matcher matcher = FACT_MARKER.matcher(userPrompt);
            while (matcher.find()) {
                markers.add("[fact:" + matcher.group(1) + "]");
            }
        }
        String markerSection = markers.isEmpty() ? "" : " " + String.join(" ", markers);
        return "Mock LLM generated document based on provided facts." + markerSection;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt, String modelName) {
        return generate(systemPrompt, userPrompt);
    }
}
