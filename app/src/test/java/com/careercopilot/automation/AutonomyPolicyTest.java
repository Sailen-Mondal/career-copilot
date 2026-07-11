package com.careercopilot.automation;

import com.careercopilot.applications.ApplicationDecision;
import com.careercopilot.applications.AutomationTier;
import com.careercopilot.applications.ApplicationCandidate;
import com.careercopilot.discovery.Job;
import com.careercopilot.discovery.JobStatus;
import com.careercopilot.profile.MasterProfile;
import com.careercopilot.profile.WorkAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AutonomyPolicy")
class AutonomyPolicyTest {

    private AutonomyPolicy policy;
    private MasterProfile profile;
    private Job goodJob;

    @BeforeEach
    void setUp() {
        policy = new AutonomyPolicy();

        profile = new MasterProfile(
                UUID.randomUUID(),
                "user-123",
                WorkAuthorization.H1B,
                true,
                130_000,
                Set.of("Remote"),
                "remote",
                Set.of("BlockedCo"),
                3,
                85
        );

        goodJob = new Job(
                UUID.randomUUID(),
                "greenhouse",
                "staff-backend-001",
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
    }

    @Test
    @DisplayName("auto-submits when score meets threshold and groundedness passes")
    void autoSubmit_whenHighScoreAndGrounded() {
        ApplicationCandidate candidate = new ApplicationCandidate(goodJob, 92, true, false, false, 0);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isTrue();
    }

    @Test
    @DisplayName("skips with SKIPPED_LOW_CONFIDENCE when score is below threshold")
    void skip_whenScoreBelowThreshold() {
        ApplicationCandidate candidate = new ApplicationCandidate(goodJob, 70, true, false, false, 0);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isFalse();
        assertThat(decision.tier()).isEqualTo(AutomationTier.SKIPPED_LOW_CONFIDENCE);
    }

    @Test
    @DisplayName("skips with SKIPPED_GROUNDEDNESS_FAILED when groundedness check fails")
    void skip_whenGroundednessCheckFails() {
        ApplicationCandidate candidate = new ApplicationCandidate(goodJob, 94, false, false, false, 0);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isFalse();
        assertThat(decision.tier()).isEqualTo(AutomationTier.SKIPPED_GROUNDEDNESS_FAILED);
    }

    @Test
    @DisplayName("blocks when company is on the blocklist")
    void block_whenCompanyOnBlocklist() {
        Job blockedJob = new Job(
                UUID.randomUUID(), "greenhouse", "some-role",
                URI.create("https://example.com/jobs/some-role"),
                "BlockedCo", "Engineer", "Remote", "remote", null, null, Set.of(), null, null, null, false, null, null, null, JobStatus.ACTIVE, null, null);
        ApplicationCandidate candidate = new ApplicationCandidate(blockedJob, 99, true, false, false, 0);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isFalse();
        assertThat(decision.tier()).isEqualTo(AutomationTier.BLOCKED);
    }

    @Test
    @DisplayName("blocks when job is not active")
    void block_whenJobNotActive() {
        Job closedJob = new Job(
                UUID.randomUUID(), "greenhouse", "closed-role",
                URI.create("https://example.com/jobs/closed-role"),
                "GoodCo", "Engineer", "Remote", "remote", null, null, Set.of(), null, null, null, false, null, null, null, JobStatus.EXPIRED, null, null);
        ApplicationCandidate candidate = new ApplicationCandidate(closedJob, 99, true, false, false, 0);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isFalse();
        assertThat(decision.tier()).isEqualTo(AutomationTier.BLOCKED);
    }

    @Test
    @DisplayName("blocks when platform circuit breaker is open")
    void block_whenCircuitBreakerOpen() {
        // Record order: job, matchScore, groundednessPassed, hasUnsupportedCustomFields, circuitBreakerOpen, submittedToday
        ApplicationCandidate candidate = new ApplicationCandidate(goodJob, 95, true, false, true, 0);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isFalse();
        assertThat(decision.tier()).isEqualTo(AutomationTier.BLOCKED);
    }

    @Test
    @DisplayName("blocks when daily cap is reached")
    void block_whenDailyCapReached() {
        ApplicationCandidate candidate = new ApplicationCandidate(goodJob, 95, true, false, false, 3);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isFalse();
        assertThat(decision.tier()).isEqualTo(AutomationTier.BLOCKED);
    }

    @Test
    @DisplayName("skips with SKIPPED_UNSUPPORTED_FIELD when custom field detected")
    void skip_whenUnsupportedCustomField() {
        // Record order: job, matchScore, groundednessPassed, hasUnsupportedCustomFields, circuitBreakerOpen, submittedToday
        ApplicationCandidate candidate = new ApplicationCandidate(goodJob, 92, true, true, false, 0);

        ApplicationDecision decision = policy.decide(profile, candidate);

        assertThat(decision.shouldSubmit()).isFalse();
        assertThat(decision.tier()).isEqualTo(AutomationTier.SKIPPED_UNSUPPORTED_FIELD);
    }
}
