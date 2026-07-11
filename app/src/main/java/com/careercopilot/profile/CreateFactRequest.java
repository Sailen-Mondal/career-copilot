package com.careercopilot.profile;

import java.time.LocalDate;
import java.util.Set;

public record CreateFactRequest(
        FactType type,
        String employer,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String bulletText,
        Set<String> skills
) {}
