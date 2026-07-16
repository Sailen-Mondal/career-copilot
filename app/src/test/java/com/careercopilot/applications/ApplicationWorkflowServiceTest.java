package com.careercopilot.applications;

import com.careercopilot.automation.*;
import com.careercopilot.discovery.JobEntity;
import com.careercopilot.discovery.JobRepository;
import com.careercopilot.discovery.JobStatus;
import com.careercopilot.generation.*;
import com.careercopilot.matching.MatchResult;
import com.careercopilot.matching.MatchingService;
import com.careercopilot.profile.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationWorkflowService Unit Test")
class ApplicationWorkflowServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private JobRepository jobRepository;
    @Mock private MasterProfileRepository masterProfileRepository;
    @Mock private MatchingService matchingService;
    @Mock private LlmGenerationService llmGenerationService;
    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private CircuitBreakerStateRepository circuitBreakerStateRepository;
    @Mock private AutomationPublisher automationPublisher;
    @Mock private KillSwitchService killSwitchService;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock private org.springframework.transaction.TransactionStatus transactionStatus;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ApplicationWorkflowService workflowService;

    private UUID jobId;
    private UUID profileId;
    private JobEntity jobEntity;
    private MasterProfileEntity profileEntity;
    private MasterProfile profileDomain;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn("false");
        jobId = UUID.randomUUID();
        profileId = UUID.randomUUID();

        jobEntity = new JobEntity();
        jobEntity.setId(jobId);
        jobEntity.setSource("greenhouse");
        jobEntity.setUrl("https://example.com/job");
        jobEntity.setStatus(JobStatus.ACTIVE);
        jobEntity.setDescriptionClean("Backend Developer");
        jobEntity.setCompany("TechCo");
        jobEntity.setTitle("Software Engineer");

        profileDomain = new MasterProfile(
                profileId,
                "default-user",
                WorkAuthorization.OTHER,
                false,
                null,
                Set.of(),
                null,
                Set.of(),
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
    }

    @Test
    @DisplayName("runs workflow successfully, generates documents and publishes to Redis queue")
    void runWorkflow_happyPath() throws Exception {
        // Arrange
        MatchResult matchResult = new MatchResult(90, true, null, Map.of("embedding", 45, "keyword", 45), "Mocked MatchResult");
        when(matchingService.scoreJob(jobId, profileId)).thenReturn(matchResult);

        ApplicationEntity application = new ApplicationEntity();
        application.setId(UUID.randomUUID());
        application.setJobId(jobId);
        application.setProfileId(profileId);
        when(applicationRepository.save(any(ApplicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedDocument resume = new GeneratedDocument(UUID.randomUUID(), application.getId(), DocumentType.RESUME, "resume content [fact:" + profileId + "]", Instant.now(), List.of(profileId));
        GeneratedDocument cover = new GeneratedDocument(UUID.randomUUID(), application.getId(), DocumentType.COVER_LETTER, "cover content [fact:" + profileId + "]", Instant.now(), List.of(profileId));
        when(llmGenerationService.generate(any(), any(), any(), eq(DocumentType.RESUME), any())).thenReturn(resume);
        when(llmGenerationService.generate(any(), any(), any(), eq(DocumentType.COVER_LETTER), any())).thenReturn(cover);

        when(circuitBreakerStateRepository.findByScope(any())).thenReturn(Optional.empty());
        when(applicationRepository.findAll()).thenReturn(List.of());
        when(killSwitchService.isHalted()).thenReturn(false);

        // Act
        ApplicationEntity result = workflowService.runWorkflow(jobId, profileId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.QUEUED.name());
        assertThat(result.getMatchScore()).isEqualTo(90);
        assertThat(result.isGroundednessCheckPassed()).isTrue();

        verify(automationPublisher, times(1)).publish(any(AutomationCommand.class));
    }

    @Test
    @DisplayName("skips publishing and blocks application if autonomy score is low")
    void runWorkflow_lowScore() throws Exception {
        // Arrange
        MatchResult matchResult = new MatchResult(50, true, null, Map.of("embedding", 25, "keyword", 25), "Mocked MatchResult");
        when(matchingService.scoreJob(jobId, profileId)).thenReturn(matchResult);

        ApplicationEntity application = new ApplicationEntity();
        application.setId(UUID.randomUUID());
        application.setJobId(jobId);
        application.setProfileId(profileId);
        when(applicationRepository.save(any(ApplicationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedDocument resume = new GeneratedDocument(UUID.randomUUID(), application.getId(), DocumentType.RESUME, "resume content [fact:" + profileId + "]", Instant.now(), List.of(profileId));
        GeneratedDocument cover = new GeneratedDocument(UUID.randomUUID(), application.getId(), DocumentType.COVER_LETTER, "cover content [fact:" + profileId + "]", Instant.now(), List.of(profileId));
        when(llmGenerationService.generate(any(), any(), any(), eq(DocumentType.RESUME), any())).thenReturn(resume);
        when(llmGenerationService.generate(any(), any(), any(), eq(DocumentType.COVER_LETTER), any())).thenReturn(cover);

        when(circuitBreakerStateRepository.findByScope(any())).thenReturn(Optional.empty());
        when(applicationRepository.findAll()).thenReturn(List.of());

        // Act
        ApplicationEntity result = workflowService.runWorkflow(jobId, profileId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.BLOCKED.name());
        verify(automationPublisher, never()).publish(any());
    }
}
