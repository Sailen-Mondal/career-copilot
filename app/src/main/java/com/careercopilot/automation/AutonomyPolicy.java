package com.careercopilot.automation;

import com.careercopilot.applications.ApplicationCandidate;
import com.careercopilot.applications.ApplicationDecision;
import com.careercopilot.applications.AutomationTier;
import com.careercopilot.profile.MasterProfile;

public final class AutonomyPolicy {
    public ApplicationDecision decide(MasterProfile profile, ApplicationCandidate candidate) {
        if (profile.blocksCompany(candidate.job().company())) {
            return ApplicationDecision.skip(AutomationTier.BLOCKED, "Company is on the blocklist.");
        }
        if (!candidate.job().isOpen()) {
            return ApplicationDecision.skip(AutomationTier.BLOCKED, "Job is not active.");
        }
        if (candidate.circuitBreakerOpen()) {
            return ApplicationDecision.skip(AutomationTier.BLOCKED, "Platform circuit breaker is open.");
        }
        if (candidate.submittedToday() >= profile.dailyApplicationCap()) {
            return ApplicationDecision.skip(AutomationTier.BLOCKED, "Daily application cap reached.");
        }
        if (!candidate.groundednessPassed()) {
            return ApplicationDecision.skip(AutomationTier.SKIPPED_GROUNDEDNESS_FAILED, "Groundedness check failed.");
        }
        if (candidate.hasUnsupportedCustomFields()) {
            return ApplicationDecision.skip(AutomationTier.SKIPPED_UNSUPPORTED_FIELD, "Unsupported custom field detected.");
        }
        if (candidate.matchScore() < profile.autonomyThreshold()) {
            return ApplicationDecision.skip(AutomationTier.SKIPPED_LOW_CONFIDENCE, "Match score is below autonomy threshold.");
        }
        return ApplicationDecision.submit("All automated submission checks passed.");
    }
}
