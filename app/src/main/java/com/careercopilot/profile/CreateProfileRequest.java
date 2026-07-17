package com.careercopilot.profile;

import java.util.Set;

public record CreateProfileRequest(
        WorkAuthorization workAuthorization,
        boolean visaSponsorshipNeeded,
        Integer salaryFloor,
        Set<String> locations,
        String remotePreference,
        Set<String> blocklistCompanies,
        int dailyApplicationCap,
        int autonomyThreshold,
        String name,
        String email,
        String phone,
        String linkedinUrl,
        String websiteUrl,
        Set<String> searchKeywords
) {}
