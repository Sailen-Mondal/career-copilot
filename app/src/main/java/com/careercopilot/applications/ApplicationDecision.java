package com.careercopilot.applications;

public record ApplicationDecision(
        AutomationTier tier,
        boolean shouldSubmit,
        String reason
) {
    public static ApplicationDecision submit(String reason) {
        return new ApplicationDecision(AutomationTier.AUTO, true, reason);
    }

    public static ApplicationDecision skip(AutomationTier tier, String reason) {
        return new ApplicationDecision(tier, false, reason);
    }
}
