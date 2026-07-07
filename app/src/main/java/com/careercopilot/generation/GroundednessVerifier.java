package com.careercopilot.generation;

import com.careercopilot.profile.ProfileFact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class GroundednessVerifier {
    private static final Pattern FACT_MARKER = Pattern.compile("\\[fact:([0-9a-fA-F-]{36})]");

    public GroundednessReport verify(GeneratedDocument document, Collection<ProfileFact> availableFacts) {
        Set<UUID> availableFactIds = availableFacts.stream()
                .map(ProfileFact::id)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> claimedFactIds = new HashSet<>(document.sourceFactIds());
        Set<UUID> markerFactIds = extractFactMarkers(document.content());
        ArrayList<String> issues = new ArrayList<>();

        for (UUID factId : claimedFactIds) {
            if (!availableFactIds.contains(factId)) {
                issues.add("Generated document references an unavailable profile fact: " + factId);
            }
        }

        for (UUID markerFactId : markerFactIds) {
            if (!availableFactIds.contains(markerFactId)) {
                issues.add("Generated document contains an unknown fact marker: " + markerFactId);
            }
            if (!claimedFactIds.contains(markerFactId)) {
                issues.add("Generated document uses a fact marker not listed in sourceFactIds: " + markerFactId);
            }
        }

        if (document.content().toLowerCase().contains("unverified:")) {
            issues.add("Generated document contains text explicitly marked as unverified.");
        }

        return new GroundednessReport(issues.isEmpty(), issues);
    }

    private Set<UUID> extractFactMarkers(String content) {
        Matcher matcher = FACT_MARKER.matcher(content);
        Set<UUID> ids = new HashSet<>();
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1)));
        }
        return ids;
    }
}
