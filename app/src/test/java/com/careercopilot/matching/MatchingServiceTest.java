package com.careercopilot.matching;

import com.careercopilot.discovery.*;
import com.careercopilot.profile.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingService Unit Test")
class MatchingServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private ProfileFactRepository profileFactRepository;
    @Mock
    private MasterProfileRepository masterProfileRepository;
    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private MatchingService matchingService;

    private UUID jobId;
    private UUID profileId;
    private JobEntity jobEntity;
    private MasterProfileEntity profileEntity;
    private MasterProfile profileDomain;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        profileId = UUID.randomUUID();

        jobEntity = new JobEntity();
        jobEntity.setId(jobId);
        jobEntity.setSource("greenhouse");
        jobEntity.setCompany("TestCompany");
        jobEntity.setTitle("Software Engineer");
        jobEntity.setLocation("San Francisco, CA");
        jobEntity.setLocationType("onsite");
        jobEntity.setStatus(JobStatus.ACTIVE);
        jobEntity.setRequiredSkills(Set.of("Java"));

        profileDomain = new MasterProfile(
                profileId,
                "user-123",
                WorkAuthorization.US_CITIZEN,
                false,
                null,
                Set.of("San Francisco, CA"),
                "onsite",
                Set.of("BlockedCo"),
                3,
                85,
                "John Doe",
                "john@example.com",
                "123",
                "linkedin",
                "website"
        );
        profileEntity = new MasterProfileEntity(profileDomain);

        lenient().when(jobRepository.findById(jobId)).thenReturn(Optional.of(jobEntity));
        lenient().when(masterProfileRepository.findById(profileId)).thenReturn(Optional.of(profileEntity));
        lenient().when(profileFactRepository.findByMasterProfileId(profileId)).thenReturn(List.of());
    }

    @Test
    @DisplayName("scores job successfully when all filters pass")
    void scoreJob_happyPath() {
        when(embeddingClient.getEmbedding(anyString())).thenReturn(new float[1536]);

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isTrue();
        assertThat(result.score()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("throws ResponseStatusException when job not found")
    void scoreJob_jobNotFound() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.scoreJob(jobId, profileId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("fails work authorization pre-filter")
    void scoreJob_workAuthMismatch() {
        jobEntity.setWorkAuthRequired("GREEN_CARD");
        profileEntity.setWorkAuthorization(WorkAuthorization.H1B);

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibilityReason()).contains("Work authorization requirement not met");
    }

    @Test
    @DisplayName("fails visa sponsorship pre-filter")
    void scoreJob_sponsorshipMismatch() {
        profileEntity.setVisaSponsorshipNeeded(true);
        jobEntity.setSponsorshipAvailable(false);

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibilityReason()).contains("Visa sponsorship needed but not available");
    }

    @Test
    @DisplayName("fails blocklist pre-filter")
    void scoreJob_blockedCompany() {
        jobEntity.setCompany("BlockedCo");

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibilityReason()).contains("Company is on the blocklist");
    }

    @Test
    @DisplayName("fails seniority filter when under-experienced for senior role")
    void scoreJob_seniorityUnderexperienced() {
        jobEntity.setSeniority("SENIOR");

        // Candidate has 1 year of experience
        ProfileFactEntity experienceFact = new ProfileFactEntity();
        experienceFact.setType(FactType.EXPERIENCE);
        experienceFact.setStartDate(LocalDate.now().minusYears(1));
        experienceFact.setEndDate(LocalDate.now());
        experienceFact.setBulletText("Developed backend code.");
        when(profileFactRepository.findByMasterProfileId(profileId)).thenReturn(List.of(experienceFact));

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibilityReason()).contains("Seniority filter: Job requires SENIOR experience");
    }

    @Test
    @DisplayName("passes seniority filter when experienced enough for senior role")
    void scoreJob_seniorityExperiencedEnough() {
        jobEntity.setSeniority("SENIOR");
        when(embeddingClient.getEmbedding(anyString())).thenReturn(new float[1536]);

        // Candidate has 5 years of experience
        ProfileFactEntity experienceFact = new ProfileFactEntity();
        experienceFact.setType(FactType.EXPERIENCE);
        experienceFact.setStartDate(LocalDate.now().minusYears(5));
        experienceFact.setEndDate(LocalDate.now());
        experienceFact.setBulletText("Developed backend code.");
        when(profileFactRepository.findByMasterProfileId(profileId)).thenReturn(List.of(experienceFact));

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isTrue();
    }

    @Test
    @DisplayName("fails seniority filter when over-qualified for junior role")
    void scoreJob_seniorityOverqualified() {
        jobEntity.setSeniority("JUNIOR");

        // Candidate has 10 years of experience
        ProfileFactEntity experienceFact = new ProfileFactEntity();
        experienceFact.setType(FactType.EXPERIENCE);
        experienceFact.setStartDate(LocalDate.now().minusYears(10));
        experienceFact.setEndDate(LocalDate.now());
        experienceFact.setBulletText("Developed backend code.");
        when(profileFactRepository.findByMasterProfileId(profileId)).thenReturn(List.of(experienceFact));

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibilityReason()).contains("Seniority filter: Job requires JUNIOR experience");
    }

    @Test
    @DisplayName("fails location filter when remote preferences mismatch")
    void scoreJob_locationPreferenceMismatch() {
        jobEntity.setLocationType("remote");
        profileEntity.setRemotePreference("onsite");

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibilityReason()).contains("Location filter: Job is remote, but candidate preference is onsite.");
    }

    @Test
    @DisplayName("fails location filter when job location is not in candidate preferred list")
    void scoreJob_locationMismatchedList() {
        jobEntity.setLocationType("onsite");
        jobEntity.setLocation("New York, NY");
        profileEntity.setLocations(Set.of("San Francisco, CA"));
        profileEntity.setRemotePreference("onsite");

        MatchResult result = matchingService.scoreJob(jobId, profileId);

        assertThat(result.eligible()).isFalse();
        assertThat(result.ineligibilityReason()).contains("Location filter: Job location 'New York, NY' does not match preferred locations");
    }
}
