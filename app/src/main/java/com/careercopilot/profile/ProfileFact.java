package com.careercopilot.profile;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ProfileFact(
        UUID id,
        UUID masterProfileId,
        FactType type,
        String employer,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String bulletText,
        Set<String> skills
) {
    public ProfileFact {
        if (id == null) {
            throw new IllegalArgumentException("ProfileFact id is required.");
        }
        if (masterProfileId == null) {
            throw new IllegalArgumentException("MasterProfile ID reference is required.");
        }
        if (type == null) {
            throw new IllegalArgumentException("ProfileFact type is required.");
        }
        skills = skills == null ? Set.of() : Set.copyOf(skills);
    }
}
