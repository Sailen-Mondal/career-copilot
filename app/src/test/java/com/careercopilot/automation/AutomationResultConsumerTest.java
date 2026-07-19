package com.careercopilot.automation;

import com.careercopilot.applications.*;
import com.careercopilot.discovery.JobEntity;
import com.careercopilot.discovery.JobRepository;
import com.careercopilot.profile.MasterProfileRepository;
import com.careercopilot.generation.GeneratedDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutomationResultConsumer Unit Test")
class AutomationResultConsumerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ApplicationService applicationService;
    @Mock private KillSwitchService killSwitchService;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private JobRepository jobRepository;
    @Mock private CircuitBreakerStateRepository circuitBreakerStateRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private MasterProfileRepository masterProfileRepository;
    @Mock private GeneratedDocumentRepository generatedDocumentRepository;
    @Mock private AutomationPublisher automationPublisher;

    @InjectMocks
    private AutomationResultConsumer consumer;

    private UUID appId;
    private UUID jobId;
    private ApplicationEntity appEntity;
    private JobEntity jobEntity;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        appId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        appEntity = new ApplicationEntity();
        appEntity.setId(appId);
        appEntity.setJobId(jobId);

        jobEntity = new JobEntity();
        jobEntity.setId(jobId);
        jobEntity.setSource("greenhouse");

        lenient().when(applicationRepository.findById(appId)).thenReturn(Optional.of(appEntity));
        lenient().when(applicationRepository.existsById(appId)).thenReturn(true);
        lenient().when(jobRepository.findById(jobId)).thenReturn(Optional.of(jobEntity));
    }

    @Test
    @DisplayName("increments errorCountWindow and trips circuit breaker on 3 consecutive failures")
    @SuppressWarnings("unchecked")
    void processMessage_failedTripsBreaker() {
        appEntity.setRetryCount(3); // Set retryCount to 3 so it doesn't trigger retry loop and marks FAILED
        Map<String, String> payload = Map.of(
                "applicationId", appId.toString(),
                "status", "failed"
        );
        when(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(payload);

        CircuitBreakerStateEntity cbState = new CircuitBreakerStateEntity();
        cbState.setId(UUID.randomUUID());
        cbState.setScope("greenhouse");
        cbState.setStatus(CircuitBreakerStatus.CLOSED);
        cbState.setErrorCountWindow(2); // Next error makes it 3

        when(circuitBreakerStateRepository.findByScope("greenhouse")).thenReturn(Optional.of(cbState));

        MapRecord<String, Object, Object> record = MapRecord.create("stream", (Map) payload);

        consumer.processMessage(record);

        // Verification of state save inside transaction instead of transitionStatus for FAILED case in processMessage
        verify(applicationRepository, atLeastOnce()).save(appEntity);
        
        ArgumentCaptor<CircuitBreakerStateEntity> captor = ArgumentCaptor.forClass(CircuitBreakerStateEntity.class);
        verify(circuitBreakerStateRepository).save(captor.capture());
        
        CircuitBreakerStateEntity savedState = captor.getValue();
        assertThat(savedState.getStatus()).isEqualTo(CircuitBreakerStatus.OPEN);
        assertThat(savedState.getErrorCountWindow()).isEqualTo(3);
        assertThat(savedState.getReason()).contains("3 consecutive failures");
    }

    @Test
    @DisplayName("resets errorCountWindow and closes circuit breaker on success")
    @SuppressWarnings("unchecked")
    void processMessage_submittedClosesBreaker() {
        Map<String, String> payload = Map.of(
                "applicationId", appId.toString(),
                "status", "submitted"
        );
        when(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(payload);

        CircuitBreakerStateEntity cbState = new CircuitBreakerStateEntity();
        cbState.setId(UUID.randomUUID());
        cbState.setScope("greenhouse");
        cbState.setStatus(CircuitBreakerStatus.OPEN);
        cbState.setErrorCountWindow(3);

        when(circuitBreakerStateRepository.findByScope("greenhouse")).thenReturn(Optional.of(cbState));

        MapRecord<String, Object, Object> record = MapRecord.create("stream", (Map) payload);

        consumer.processMessage(record);

        verify(applicationService).transitionStatus(appId, ApplicationStatus.SUBMITTED);
        
        ArgumentCaptor<CircuitBreakerStateEntity> captor = ArgumentCaptor.forClass(CircuitBreakerStateEntity.class);
        verify(circuitBreakerStateRepository).save(captor.capture());
        
        CircuitBreakerStateEntity savedState = captor.getValue();
        assertThat(savedState.getStatus()).isEqualTo(CircuitBreakerStatus.CLOSED);
        assertThat(savedState.getErrorCountWindow()).isEqualTo(0);
        assertThat(savedState.getTrippedAt()).isNull();
    }
}
