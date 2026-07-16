package com.careercopilot.applications;

import com.careercopilot.discovery.JobEntity;
import com.careercopilot.discovery.JobRepository;
import com.careercopilot.generation.GeneratedDocumentRepository;
import com.careercopilot.profile.*;
import com.careercopilot.shared.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationController Unit Test")
class ApplicationControllerTest {

    @Mock private ApplicationService applicationService;
    @Mock private ApplicationWorkflowService applicationWorkflowService;
    @Mock private JobRepository jobRepository;
    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private LlmClient llmClient;
    @Mock private MasterProfileRepository masterProfileRepository;
    @Mock private ProfileFactRepository profileFactRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ApplicationController applicationController;

    private UUID appId;
    private UUID jobId;
    private UUID profileId;
    private ApplicationEntity applicationEntity;
    private JobEntity jobEntity;
    private MasterProfileEntity masterProfileEntity;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        profileId = UUID.randomUUID();

        applicationEntity = new ApplicationEntity();
        applicationEntity.setId(appId);
        applicationEntity.setJobId(jobId);
        applicationEntity.setProfileId(profileId);

        jobEntity = new JobEntity();
        jobEntity.setId(jobId);
        jobEntity.setCompany("TechCorp");
        jobEntity.setTitle("Software Engineer");

        MasterProfile profileDomain = new MasterProfile(
                profileId,
                "default-user",
                WorkAuthorization.US_CITIZEN,
                false,
                null,
                Set.of(),
                null,
                Set.of(),
                3,
                75,
                "John Doe",
                "john@example.com",
                "123-456-7890",
                "https://linkedin.com/in/johndoe",
                "https://johndoe.com"
        );
        masterProfileEntity = new MasterProfileEntity(profileDomain);
    }

    @Test
    @DisplayName("should resolve standard fields using heuristics and custom fields using AI Brain")
    void fillFormFields_success() throws Exception {
        // Arrange
        List<FormFieldDto> fields = List.of(
                new FormFieldDto("input[name='first_name']", "first_name", "First Name", "input", List.of(), true),
                new FormFieldDto("input[name='email']", "email", "Email Address", "input", List.of(), true),
                new FormFieldDto("textarea[name='custom_q']", "custom_q", "Why do you want to join?", "textarea", List.of(), false)
        );

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(applicationEntity));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(jobEntity));
        when(masterProfileRepository.findById(profileId)).thenReturn(Optional.of(masterProfileEntity));
        when(profileFactRepository.findByMasterProfileId(profileId)).thenReturn(List.of());

        // Mock LLM Client & ObjectMapper for the AI brain custom question part
        when(llmClient.generate(anyString(), anyString())).thenReturn("{\"custom_q\": \"Because I love TechCorp!\"}");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("custom_q", "Because I love TechCorp!"));

        // Act
        ResponseEntity<Map<String, String>> response = applicationController.fillFormFields(appId, fields);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("first_name", "John");
        assertThat(body).containsEntry("email", "john@example.com");
        assertThat(body).containsEntry("custom_q", "Because I love TechCorp!");
    }
}
