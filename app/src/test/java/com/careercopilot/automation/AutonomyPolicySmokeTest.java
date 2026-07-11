package com.careercopilot.automation;

import com.careercopilot.applications.ApplicationCandidate;
import com.careercopilot.applications.ApplicationDecision;
import com.careercopilot.applications.AutomationTier;
import com.careercopilot.discovery.Job;
import com.careercopilot.discovery.JobStatus;
import com.careercopilot.profile.MasterProfile;
import com.careercopilot.profile.WorkAuthorization;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

public final class AutonomyPolicySmokeTest {
    public static void main(String[] args) {
        MasterProfile profile = new MasterProfile(
                UUID.randomUUID(),
                "user-123",
                WorkAuthorization.H1B,
                true,
                130000,
                Set.of("Remote"),
                "remote",
                Set.of("BlockedCo"),
                3,
                85
        );

        Job job = new Job(
                UUID.randomUUID(),
                "greenhouse",
                "staff-backend",
                URI.create("https://example.com/jobs/staff-backend"),
                "GoodCo",
                "Staff Backend Engineer",
                "Remote",
                "remote",
                null,
                null,
                Set.of("Java", "PostgreSQL"),
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                JobStatus.ACTIVE,
                null,
                null
        );

        AutonomyPolicy policy = new AutonomyPolicy();
        ApplicationDecision submit = policy.decide(profile, new ApplicationCandidate(job, 92, true, false, false, 0));
        require(submit.shouldSubmit(), "Expected high-confidence grounded candidate to auto-submit.");

        ApplicationDecision lowScore = policy.decide(profile, new ApplicationCandidate(job, 70, true, false, false, 0));
        require(lowScore.tier() == AutomationTier.SKIPPED_LOW_CONFIDENCE, "Expected low score to skip.");

        ApplicationDecision unsafe = policy.decide(profile, new ApplicationCandidate(job, 94, false, false, false, 0));
        require(unsafe.tier() == AutomationTier.SKIPPED_GROUNDEDNESS_FAILED, "Expected groundedness failure to skip.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
