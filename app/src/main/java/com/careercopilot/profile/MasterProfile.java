package com.careercopilot.profile;

import java.util.Set;
import java.util.UUID;

public record MasterProfile(
        UUID id,
        WorkAuthorization workAuthorization,
        boolean visaSponsorshipNeeded,
        int salaryFloor,
        Set<String> locations,
        String remotePreference,
        Set<String> blocklistCompanies,
        int dailyApplicationCap,
        int autonomyThreshold
) {
    public MasterProfile {
        if (id == null) {
            throw new IllegalArgumentException("MasterProfile id is required.");
        }
        if (dailyApplicationCap < 0) {
            throw new IllegalArgumentException("Daily application cap cannot be negative.");
        }
        if (autonomyThreshold < 0 || autonomyThreshold > 100) {
            throw new IllegalArgumentException("Autonomy threshold must be between 0 and 100.");
        }
        locations = locations == null ? Set.of() : Set.copyOf(locations);
        blocklistCompanies = blocklistCompanies == null ? Set.of() : Set.copyOf(blocklistCompanies);
    }

    public boolean blocksCompany(String company) {
        if (company == null) {
            return false;
        }
        return blocklistCompanies.stream().anyMatch(blocked -> blocked.equalsIgnoreCase(company));
    }
}
