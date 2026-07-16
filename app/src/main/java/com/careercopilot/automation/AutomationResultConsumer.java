package com.careercopilot.automation;

import com.careercopilot.applications.ApplicationEntity;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.applications.ApplicationService;
import com.careercopilot.applications.ApplicationStatus;
import com.careercopilot.discovery.JobEntity;
import com.careercopilot.discovery.JobRepository;
import com.careercopilot.profile.MasterProfileEntity;
import com.careercopilot.profile.MasterProfileRepository;
import com.careercopilot.generation.GeneratedDocumentEntity;
import com.careercopilot.generation.GeneratedDocumentRepository;
import com.careercopilot.discovery.EmbeddingClient;
import com.careercopilot.profile.ProfileFactEntity;
import com.careercopilot.profile.ProfileFactRepository;
import com.careercopilot.shared.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes {@link AutomationResult} payloads from the Redis results stream
 * and updates application status and audit trail accordingly.
 *
 * <p>Runs a background polling thread that starts after the application
 * context is fully initialized. Respects the kill switch.
 */
@Service
public class AutomationResultConsumer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AutomationResultConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationService applicationService;
    private final KillSwitchService killSwitchService;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CircuitBreakerStateRepository circuitBreakerStateRepository;
    private final TransactionTemplate transactionTemplate;
    private final MasterProfileRepository masterProfileRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final AutomationPublisher automationPublisher;
    private final AnswerCacheRepository answerCacheRepository;
    private final EmbeddingClient embeddingClient;
    private final LlmClient llmClient;
    private final ProfileFactRepository profileFactRepository;

    private final String resultsStream;
    private final String consumerGroup;
    private final String consumerName;

    private volatile boolean running = true;
    private Thread consumerThread;

    public AutomationResultConsumer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ApplicationService applicationService,
            KillSwitchService killSwitchService,
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            CircuitBreakerStateRepository circuitBreakerStateRepository,
            PlatformTransactionManager transactionManager,
            MasterProfileRepository masterProfileRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            AutomationPublisher automationPublisher,
            AnswerCacheRepository answerCacheRepository,
            EmbeddingClient embeddingClient,
            LlmClient llmClient,
            ProfileFactRepository profileFactRepository,
            @Value("${automation.streams.results-stream:cc:automation:results}") String resultsStream,
            @Value("${automation.streams.consumer-group:spring-consumers}") String consumerGroup,
            @Value("${automation.streams.consumer-name:app-1}") String consumerName) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.applicationService = applicationService;
        this.killSwitchService = killSwitchService;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.circuitBreakerStateRepository = circuitBreakerStateRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.masterProfileRepository = masterProfileRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.automationPublisher = automationPublisher;
        this.answerCacheRepository = answerCacheRepository;
        this.embeddingClient = embeddingClient;
        this.llmClient = llmClient;
        this.profileFactRepository = profileFactRepository;
        this.resultsStream = resultsStream;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        createConsumerGroupIfNeeded();
        consumerThread = new Thread(this::pollLoop, "automation-result-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("AutomationResultConsumer started — stream={}, group={}, consumer={}",
                resultsStream, consumerGroup, consumerName);
    }

    /** Stops the polling loop (called during shutdown). */
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    private void createConsumerGroupIfNeeded() {
        try {
            redisTemplate.opsForStream().createGroup(resultsStream, ReadOffset.from("0"), consumerGroup);
            log.info("Created consumer group '{}' on stream '{}'", consumerGroup, resultsStream);
        } catch (Exception e) {
            // BUSYGROUP: consumer group already exists — safe to ignore
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer group '{}' already exists on stream '{}'",
                        consumerGroup, resultsStream);
            } else {
                log.warn("Could not create consumer group (Redis may not be available yet): {}",
                        e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void pollLoop() {
        while (running) {
            try {
                // Block up to 5 seconds waiting for messages
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                        Consumer.from(consumerGroup, consumerName),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(5)),
                        StreamOffset.create(resultsStream, ReadOffset.lastConsumed())
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> message : messages) {
                    processMessage(message);
                    // ACK the message
                    redisTemplate.opsForStream().acknowledge(resultsStream, consumerGroup, message.getId());
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.error("Error in automation result consumer: {}", e.getMessage());
                try {
                    Thread.sleep(2000); // Back off on errors
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("AutomationResultConsumer stopped");
    }

    void processMessage(MapRecord<String, Object, Object> message) {
        try {
            Map<String, String> fields = objectMapper.convertValue(
                    message.getValue(), new TypeReference<Map<String, String>>() {});

            String applicationIdStr = fields.get("applicationId");
            String status = fields.get("status");
            String screenshotPath = fields.getOrDefault("screenshotPath", "");
            String confirmationUrl = fields.get("platformResponse");

            if (applicationIdStr == null || status == null) {
                log.warn("Invalid result message — missing applicationId or status: {}",
                        message.getId());
                return;
            }

            UUID applicationId = UUID.fromString(applicationIdStr);

            String unsupportedFieldsJson = fields.get("unsupportedFields");
            List<String> unsupportedFieldsList = new ArrayList<>();
            if (unsupportedFieldsJson != null && !unsupportedFieldsJson.isEmpty()) {
                try {
                    unsupportedFieldsList = objectMapper.readValue(unsupportedFieldsJson, new TypeReference<List<String>>() {});
                } catch (Exception e) {
                    log.error("Failed to parse unsupportedFields", e);
                }
            }

            // Handle Retry Logic for failures
            if ("failed".equals(status)) {
                final boolean[] retried = {false};
                transactionTemplate.executeWithoutResult(txnStatus -> {
                    applicationRepository.findById(applicationId).ifPresent(app -> {
                        int currentRetries = app.getRetryCount();
                        app.setLastError(fields.getOrDefault("logs", "No logs provided"));
                        if (currentRetries < 3) {
                            int newRetries = currentRetries + 1;
                            app.setRetryCount(newRetries);
                            app.setStatus(ApplicationStatus.QUEUED.name());

                            List<String> trail = app.getAuditTrail();
                            if (trail == null) trail = new ArrayList<>();
                            trail.add("[" + Instant.now().toString() + "] Playwright worker failed. Retry attempt " + newRetries + "/3.");
                            app.setAuditTrail(trail);
                            applicationRepository.save(app);

                            retried[0] = true;
                            log.info("Application {} failed. Scheduling retry attempt {}/3.", applicationId, newRetries);
                        } else {
                            app.setStatus(ApplicationStatus.FAILED.name());
                            List<String> trail = app.getAuditTrail();
                            if (trail == null) trail = new ArrayList<>();
                            trail.add("[" + Instant.now().toString() + "] Playwright worker failed. Max retry limit (3) reached. Application marked FAILED.");
                            app.setAuditTrail(trail);
                            applicationRepository.save(app);
                            log.warn("Application {} failed. Max retry limit reached.", applicationId);
                        }
                    });
                });

                if (retried[0]) {
                    // Re-publish to the stream for retry
                    applicationRepository.findById(applicationId).ifPresent(this::republishAutomationCommand);
                    return; // Skip circuit breaker check until final failure
                }
            } else if ("shadow_completed".equals(status) && !unsupportedFieldsList.isEmpty()) {
                final List<String> finalUnsupportedFields = unsupportedFieldsList;
                final Map<String, String>[] customAnswersMapContainer = new Map[]{new HashMap<>()};
                final boolean[] allResolved = {true};
                final List<String> unresolvedFields = new ArrayList<>();

                transactionTemplate.executeWithoutResult(txnStatus -> {
                    applicationRepository.findById(applicationId).ifPresent(app -> {
                        jobRepository.findById(app.getJobId()).ifPresent(job -> {
                            Map<String, String> resolvedAnswers = resolveUnsupportedFields(applicationId, finalUnsupportedFields, job);
                            
                            for (String fieldStr : finalUnsupportedFields) {
                                String[] parts = fieldStr.split("::", 2);
                                String identifier = parts[0];
                                String questionText = parts.length > 1 ? parts[1] : "";
                                
                                if (!resolvedAnswers.containsKey(identifier) && !resolvedAnswers.containsKey(questionText)) {
                                    allResolved[0] = false;
                                    unresolvedFields.add(fieldStr);
                                }
                            }
                            
                            customAnswersMapContainer[0] = resolvedAnswers;
                        });
                    });
                });

                if (!allResolved[0]) {
                    applicationService.transitionStatus(applicationId, ApplicationStatus.BLOCKED);
                    applicationService.appendAuditEntry(applicationId,
                            "Application blocked due to unhandled form fields: " + unresolvedFields);
                    return;
                } else if (customAnswersMapContainer[0] != null && !customAnswersMapContainer[0].isEmpty()) {
                    final boolean[] rescheduled = {false};
                    transactionTemplate.executeWithoutResult(txnStatus -> {
                        applicationRepository.findById(applicationId).ifPresent(app -> {
                            int currentRetries = app.getRetryCount();
                            if (currentRetries < 3) {
                                app.setRetryCount(currentRetries + 1);
                                app.setStatus(ApplicationStatus.QUEUED.name());
                                List<String> trail = app.getAuditTrail();
                                if (trail == null) trail = new ArrayList<>();
                                trail.add("[" + Instant.now().toString() + "] Shadow run found custom questions. Resolved all, re-dispatching with custom answers.");
                                app.setAuditTrail(trail);
                                applicationRepository.save(app);
                                rescheduled[0] = true;
                            } else {
                                app.setStatus(ApplicationStatus.BLOCKED.name());
                                List<String> trail = app.getAuditTrail();
                                if (trail == null) trail = new ArrayList<>();
                                trail.add("[" + Instant.now().toString() + "] Max attempts to resolve custom questions reached.");
                                app.setAuditTrail(trail);
                                applicationRepository.save(app);
                            }
                        });
                    });

                    if (rescheduled[0]) {
                        applicationRepository.findById(applicationId).ifPresent(app -> {
                            republishWithCustomAnswers(app, customAnswersMapContainer[0]);
                        });
                        return;
                    }
                }
            } else {
                // Map worker status to ApplicationStatus
                ApplicationStatus appStatus = switch (status) {
                    case "shadow_completed" -> ApplicationStatus.READY;
                    case "submitted" -> ApplicationStatus.SUBMITTED;
                    case "verifying" -> ApplicationStatus.VERIFYING;
                    default -> ApplicationStatus.BLOCKED;
                };

                applicationService.transitionStatus(applicationId, appStatus);
                applicationService.appendAuditEntry(applicationId,
                        "Playwright worker completed: status=" + status
                                + (screenshotPath.isEmpty() ? "" : ", screenshot=" + screenshotPath));

                // Save confirmation URL if available
                if ("submitted".equals(status) && confirmationUrl != null && !confirmationUrl.isEmpty()) {
                    transactionTemplate.executeWithoutResult(txnStatus -> {
                        applicationRepository.findById(applicationId).ifPresent(app -> {
                            app.setExternalApplicationId(confirmationUrl);
                            applicationRepository.save(app);
                        });
                    });
                }
            }

            // Update circuit breaker state based on success/failure
            try {
                transactionTemplate.executeWithoutResult(txnStatus -> {
                    applicationRepository.findById(applicationId).ifPresent(app -> {
                        jobRepository.findById(app.getJobId()).ifPresent(job -> {
                            String scope = job.getSource();
                            if (scope != null) {
                                CircuitBreakerStateEntity cbState = circuitBreakerStateRepository.findByScope(scope)
                                        .orElseGet(() -> {
                                            CircuitBreakerStateEntity newState = new CircuitBreakerStateEntity();
                                            newState.setId(UUID.randomUUID());
                                            newState.setScope(scope);
                                            newState.setStatus(CircuitBreakerStatus.CLOSED);
                                            newState.setErrorCountWindow(0);
                                            return newState;
                                        });

                                if ("failed".equals(status)) {
                                    if (cbState.getStatus() == CircuitBreakerStatus.CLOSED) {
                                        int errors = cbState.getErrorCountWindow() + 1;
                                        cbState.setErrorCountWindow(errors);
                                        if (errors >= 3) {
                                            cbState.setStatus(CircuitBreakerStatus.OPEN);
                                            cbState.setTrippedAt(Instant.now());
                                            cbState.setReason("3 consecutive failures in Playwright worker");
                                            log.warn("Circuit Breaker TRIPPED (OPEN) for scope: {}", scope);
                                        }
                                    }
                                } else if ("shadow_completed".equals(status) || "submitted".equals(status)) {
                                    cbState.setErrorCountWindow(0);
                                    cbState.setStatus(CircuitBreakerStatus.CLOSED);
                                    cbState.setTrippedAt(null);
                                    cbState.setReason(null);
                                }
                                circuitBreakerStateRepository.save(cbState);
                            }
                        });
                    });
                });
            } catch (Exception cbEx) {
                log.error("Failed to update circuit breaker state for application " + applicationIdStr, cbEx);
            }

            log.info("Processed automation result: applicationId={}, status={}",
                    applicationIdStr, status);
        } catch (Exception e) {
            log.error("Failed to process automation result message {}: {}",
                    message.getId(), e.getMessage());
        }
    }

    private void republishAutomationCommand(ApplicationEntity app) {
        try {
            JobEntity jobEntity = jobRepository.findById(app.getJobId())
                    .orElseThrow(() -> new IllegalStateException("Job not found: " + app.getJobId()));
            MasterProfileEntity profile = masterProfileRepository.findById(app.getProfileId())
                    .orElseThrow(() -> new IllegalStateException("Profile not found: " + app.getProfileId()));

            String resumeContent = "";
            if (app.getResumeVersionId() != null) {
                resumeContent = generatedDocumentRepository.findById(app.getResumeVersionId())
                        .map(GeneratedDocumentEntity::getContent)
                        .orElse("");
            }

            String coverLetterContent = "";
            if (app.getCoverLetterVersionId() != null) {
                coverLetterContent = generatedDocumentRepository.findById(app.getCoverLetterVersionId())
                        .map(GeneratedDocumentEntity::getContent)
                        .orElse("");
            }

            String customAnswersJson = "{}";
            try {
                List<AnswerCacheEntity> cached = answerCacheRepository.findAll().stream()
                        .filter(a -> a.getScope() == null || a.getScope().equalsIgnoreCase(jobEntity.getCompany()) || a.getScope().equalsIgnoreCase(jobEntity.getSource()))
                        .toList();
                Map<String, String> answersMap = new HashMap<>();
                for (AnswerCacheEntity cache : cached) {
                    answersMap.put(cache.getQuestionText(), cache.getAnswerText());
                }
                customAnswersJson = objectMapper.writeValueAsString(answersMap);
            } catch (Exception e) {
                log.warn("Failed to fetch custom answers for republishing", e);
            }

            AutomationCommand command = new AutomationCommand(
                    app.getId().toString(),
                    jobEntity.getUrl(),
                    "false".equalsIgnoreCase(redisTemplate.opsForValue().get("cc:automation:shadow-mode")) ? "live" : "shadow",
                    app.getProfileId().toString(),
                    app.getResumeVersionId() != null ? app.getResumeVersionId().toString() : "",
                    resumeContent,
                    app.getCoverLetterVersionId() != null ? app.getCoverLetterVersionId().toString() : "",
                    coverLetterContent,
                    profile.getName(),
                    profile.getEmail(),
                    profile.getPhone(),
                    profile.getLinkedinUrl(),
                    profile.getWebsiteUrl(),
                    customAnswersJson
            );

            automationPublisher.publish(command);
            log.info("Re-dispatched application {} to Redis automation stream for retry", app.getId());
        } catch (Exception e) {
            log.error("Failed to republish automation command for retry", e);
        }
    }

    private Map<String, String> resolveUnsupportedFields(UUID applicationId, List<String> unsupportedFields, JobEntity job) {
        Map<String, String> resolved = new HashMap<>();
        
        List<ProfileFactEntity> facts = profileFactRepository.findByMasterProfileId(
                applicationRepository.findById(applicationId).map(ApplicationEntity::getProfileId).orElse(null)
        );
        if (facts == null || facts.isEmpty()) {
            return resolved;
        }

        for (String fieldStr : unsupportedFields) {
            String[] parts = fieldStr.split("::", 2);
            String identifier = parts[0];
            String questionText = parts.length > 1 ? parts[1] : "";
            
            if (questionText.isBlank()) {
                if (identifier.contains(" ") || identifier.contains("_")) {
                    questionText = identifier.replace("_", " ");
                } else {
                    continue;
                }
            }

            String lowerQ = questionText.toLowerCase();
            if (lowerQ.contains("resume") || lowerQ.contains("coverletter") || lowerQ.contains("cv") ||
                lowerQ.contains("firstname") || lowerQ.contains("lastname") || lowerQ.contains("email") ||
                lowerQ.contains("phone")) {
                continue;
            }

            Optional<AnswerCacheEntity> cachedOpt = answerCacheRepository.findExactMatch(questionText, job.getCompany());
            if (cachedOpt.isEmpty()) {
                cachedOpt = answerCacheRepository.findExactMatch(questionText, null);
            }
            
            if (cachedOpt.isEmpty()) {
                try {
                    float[] questionEmb = embeddingClient.getEmbedding(questionText);
                    cachedOpt = answerCacheRepository.findSemanticMatch(questionEmb, job.getCompany());
                    if (cachedOpt.isEmpty()) {
                        cachedOpt = answerCacheRepository.findSemanticMatch(questionEmb, null);
                    }
                } catch (Exception e) {
                    log.warn("Failed semantic search for custom question: " + questionText, e);
                }
            }

            if (cachedOpt.isPresent()) {
                String answer = cachedOpt.get().getAnswerText();
                resolved.put(identifier, answer);
                resolved.put(questionText, answer);
                log.info("Found cached answer for question '{}' (key={})", questionText, identifier);
            } else {
                try {
                    String systemPrompt = """
                            You are an automated job application assistant. Your task is to write a concise, professional answer to a custom application question on behalf of the candidate.
                            Use the provided candidate profile facts to answer the question accurately and professionally.
                            Do not exaggerate or hallucinate. Keep the answer under 100 words.
                            Output ONLY the plain text of the answer. No intro, no quotes, no markdown.
                            """;

                    StringBuilder userPromptBuilder = new StringBuilder();
                    userPromptBuilder.append("QUESTION: ").append(questionText).append("\n\nCANDIDATE FACTS:\n");
                    for (ProfileFactEntity f : facts) {
                        userPromptBuilder.append("- ").append(f.getBulletText()).append("\n");
                    }

                    String answer = llmClient.generate(systemPrompt, userPromptBuilder.toString());
                    answer = answer.trim();
                    if (answer.startsWith("\"") && answer.endsWith("\"")) {
                        answer = answer.substring(1, answer.length() - 1).trim();
                    }

                    float[] questionEmb = embeddingClient.getEmbedding(questionText);
                    AnswerCacheEntity newCache = new AnswerCacheEntity(
                            UUID.randomUUID(),
                            questionText,
                            questionEmb,
                            answer,
                            job.getCompany(),
                            Instant.now()
                    );
                    answerCacheRepository.save(newCache);

                    resolved.put(identifier, answer);
                    resolved.put(questionText, answer);
                    log.info("Generated and cached answer for question '{}' (key={})", questionText, identifier);
                } catch (Exception e) {
                    log.error("Failed to generate answer for question: " + questionText, e);
                }
            }
        }
        return resolved;
    }

    private void republishWithCustomAnswers(ApplicationEntity app, Map<String, String> customAnswers) {
        try {
            JobEntity jobEntity = jobRepository.findById(app.getJobId())
                    .orElseThrow(() -> new IllegalStateException("Job not found: " + app.getJobId()));
            MasterProfileEntity profile = masterProfileRepository.findById(app.getProfileId())
                    .orElseThrow(() -> new IllegalStateException("Profile not found: " + app.getProfileId()));

            String resumeContent = "";
            if (app.getResumeVersionId() != null) {
                resumeContent = generatedDocumentRepository.findById(app.getResumeVersionId())
                        .map(GeneratedDocumentEntity::getContent)
                        .orElse("");
            }

            String coverLetterContent = "";
            if (app.getCoverLetterVersionId() != null) {
                coverLetterContent = generatedDocumentRepository.findById(app.getCoverLetterVersionId())
                        .map(GeneratedDocumentEntity::getContent)
                        .orElse("");
            }

            String customAnswersJson = objectMapper.writeValueAsString(customAnswers);

            AutomationCommand command = new AutomationCommand(
                    app.getId().toString(),
                    jobEntity.getUrl(),
                    "false".equalsIgnoreCase(redisTemplate.opsForValue().get("cc:automation:shadow-mode")) ? "live" : "shadow",
                    app.getProfileId().toString(),
                    app.getResumeVersionId() != null ? app.getResumeVersionId().toString() : "",
                    resumeContent,
                    app.getCoverLetterVersionId() != null ? app.getCoverLetterVersionId().toString() : "",
                    coverLetterContent,
                    profile.getName(),
                    profile.getEmail(),
                    profile.getPhone(),
                    profile.getLinkedinUrl(),
                    profile.getWebsiteUrl(),
                    customAnswersJson
            );

            automationPublisher.publish(command);
            log.info("Re-dispatched application {} with custom answers: {}", app.getId(), customAnswersJson);
        } catch (Exception e) {
            log.error("Failed to republish command with custom answers", e);
        }
    }
}
