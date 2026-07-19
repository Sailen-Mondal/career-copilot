package com.careercopilot.generation;

import com.careercopilot.profile.ProfileFact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class GroundednessVerifier {
    private static final Pattern FACT_MARKER = Pattern.compile("\\[fact:([0-9a-fA-F-]{36})]");

    public GroundednessReport verify(GeneratedDocument document, Collection<ProfileFact> availableFacts) {
        ArrayList<String> issues = new ArrayList<>();

        // 1. Reject empty profile facts
        if (availableFacts == null || availableFacts.isEmpty()) {
            issues.add("Candidate profile has no verified facts.");
            return new GroundednessReport(false, issues);
        }

        Set<UUID> availableFactIds = availableFacts.stream()
                .map(ProfileFact::id)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> claimedFactIds = new HashSet<>(document.sourceFactIds());
        Set<UUID> markerFactIds = extractFactMarkers(document.content());

        // 2. Reject generated content with no [fact:<UUID>] markers
        if (markerFactIds.isEmpty()) {
            issues.add("Generated document contains no fact markers.");
        }

        // 3. Reject unknown markers
        for (UUID markerFactId : markerFactIds) {
            if (!availableFactIds.contains(markerFactId)) {
                issues.add("Generated document contains an unknown fact marker: " + markerFactId);
            }
            if (!claimedFactIds.contains(markerFactId)) {
                issues.add("Generated document uses a fact marker not listed in sourceFactIds: " + markerFactId);
            }
        }

        // 4. Reject sourceFactIds references that are not actually in the document markers or available facts
        for (UUID factId : claimedFactIds) {
            if (!availableFactIds.contains(factId)) {
                issues.add("Generated document references an unavailable profile fact: " + factId);
            }
            if (!markerFactIds.contains(factId)) {
                issues.add("Generated document lists a sourceFactId that is not cited in the content: " + factId);
            }
        }

        // 5. Reject unverified text
        if (document.content().toLowerCase().contains("unverified:")) {
            issues.add("Generated document contains text explicitly marked as unverified.");
        }

        // 6. Reject unmarked factual content (sentences that make claims but lack markers)
        String[] segments = document.content().split("[.!?\n]+");
        List<String> sentences = new ArrayList<>();
        for (String seg : segments) {
            String trimmed = seg.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("[fact:") && trimmed.endsWith("]") && !sentences.isEmpty()) {
                int lastIdx = sentences.size() - 1;
                sentences.set(lastIdx, sentences.get(lastIdx) + " " + trimmed);
            } else {
                sentences.add(trimmed);
            }
        }

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Skip boilerplate greetings/closings, transitions, and contact fields
            String lower = trimmed.toLowerCase();
            if (lower.startsWith("dear") ||
                lower.startsWith("sincerely") ||
                lower.startsWith("hello") ||
                lower.startsWith("subject:") ||
                lower.startsWith("resume") ||
                lower.startsWith("cover letter") ||
                lower.contains("apply") ||
                lower.contains("applying") ||
                lower.contains("interest") ||
                lower.contains("excited") ||
                lower.contains("look forward") ||
                lower.contains("hear from") ||
                lower.contains("opportunity") ||
                lower.contains("thank") ||
                lower.contains("regards") ||
                lower.contains("hiring manager") ||
                lower.contains("recruiter") ||
                lower.contains("phone:") ||
                lower.contains("email:") ||
                lower.contains("linkedin:") ||
                lower.contains("github:") ||
                lower.contains("address:") ||
                lower.contains("portfolio:")) {
                continue;
            }
            // Skip very short sentences/phrases (e.g. headings, names, dates, address parts)
            if (trimmed.split("\\s+").length < 4) {
                continue;
            }
            boolean hasMarker = FACT_MARKER.matcher(trimmed).find();
            if (!hasMarker) {
                boolean isFactual = false;
                if (lower.contains("built") || lower.contains("develop") || lower.contains("design") ||
                    lower.contains("lead") || lower.contains("led") || lower.contains("manag") ||
                    lower.contains("implement") || lower.contains("engineer") || lower.contains("creat") ||
                    lower.contains("work") || lower.contains("experience") || lower.contains("responsib") ||
                    lower.matches(".*\\d+.*")) { // contains digits
                    isFactual = true;
                }
                if (isFactual) {
                    issues.add("Sentence contains unmarked factual claims: " + trimmed);
                }
            }
        }

        return new GroundednessReport(issues.isEmpty(), issues);
    }

    public Set<UUID> extractFactMarkers(String content) {
        Matcher matcher = FACT_MARKER.matcher(content);
        Set<UUID> ids = new HashSet<>();
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1)));
        }
        return ids;
    }
}
