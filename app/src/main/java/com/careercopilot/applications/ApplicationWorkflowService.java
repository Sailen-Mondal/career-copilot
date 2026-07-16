package com.careercopilot.applications;

import com.careercopilot.automation.*;
import com.careercopilot.discovery.Job;
import com.careercopilot.discovery.JobEntity;
import com.careercopilot.discovery.JobRepository;
import com.careercopilot.generation.*;
import com.careercopilot.matching.MatchResult;
import com.careercopilot.matching.MatchingService;
import com.careercopilot.profile.MasterProfile;
import com.careercopilot.profile.MasterProfileEntity;
import com.careercopilot.profile.MasterProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ApplicationWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationWorkflowService.class);

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final MasterProfileRepository masterProfileRepository;
    private final MatchingService matchingService;
    private final LlmGenerationService llmGenerationService;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final CircuitBreakerStateRepository circuitBreakerStateRepository;
    private final AutomationPublisher automationPublisher;
    private final KillSwitchService killSwitchService;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;

    public ApplicationWorkflowService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            MasterProfileRepository masterProfileRepository,
            MatchingService matchingService,
            LlmGenerationService llmGenerationService,
            GeneratedDocumentRepository generatedDocumentRepository,
            CircuitBreakerStateRepository circuitBreakerStateRepository,
            AutomationPublisher automationPublisher,
            KillSwitchService killSwitchService,
            PlatformTransactionManager transactionManager,
            StringRedisTemplate redisTemplate) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.masterProfileRepository = masterProfileRepository;
        this.matchingService = matchingService;
        this.llmGenerationService = llmGenerationService;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.circuitBreakerStateRepository = circuitBreakerStateRepository;
        this.automationPublisher = automationPublisher;
        this.killSwitchService = killSwitchService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.redisTemplate = redisTemplate;
    }

    public ApplicationEntity runWorkflow(UUID jobId, UUID profileId) {
        log.info("Running application workflow for jobId={}, profileId={}", jobId, profileId);

        // 1. Load job and profile
        JobEntity jobEntity = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
        MasterProfileEntity profileEntity = masterProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found: " + profileId));

        // 2. Score job on backend
        MatchResult matchResult = matchingService.scoreJob(jobId, profileId);
        log.info("Scored job: eligible={}, score={}", matchResult.eligible(), matchResult.score());

        // 3. Create application entity (status GENERATING, backend-driven score)
        UUID applicationId = UUID.randomUUID();
        List<String> auditTrail = new ArrayList<>();
        auditTrail.add(timestamp() + " Workflow started. Match score computed: " + matchResult.score()
                + " (Reasoning: " + matchResult.reasoning() + ")");
        if (!matchResult.eligible()) {
            auditTrail.add(timestamp() + " Matcher marked job ineligible: " + matchResult.ineligibilityReason());
        }

        final ApplicationEntity[] appContainer = new ApplicationEntity[1];
        appContainer[0] = transactionTemplate.execute(status -> {
            ApplicationEntity app = new ApplicationEntity();
            app.setId(applicationId);
            app.setJobId(jobId);
            app.setProfileId(profileId);
            app.setMatchScore(matchResult.score());
            app.setMatchScoreBreakdown(matchResult.breakdown());
            app.setStatus(ApplicationStatus.GENERATING.name());
            app.setAutomationTier(AutomationTier.AUTO.name());
            app.setGroundednessCheckPassed(false);
            app.setGroundednessReport(Map.of());
            app.setAuditTrail(new ArrayList<>(auditTrail));
            app.setCreatedAt(Instant.now());
            return applicationRepository.save(app);
        });

        // 4. Generate documents and check groundedness
        boolean groundednessPassed = true;
        Map<String, Object> groundednessReport = new HashMap<>();
        GeneratedDocument resumeDoc = null;
        GeneratedDocument coverLetterDoc = null;

        // Verify if candidate has facts at all
        try {
            resumeDoc = llmGenerationService.generate(applicationId, profileId, jobId, DocumentType.RESUME, jobEntity.getDescriptionClean());
            final GeneratedDocument finalResume = resumeDoc;
            UUID resumeEntityId = transactionTemplate.execute(status -> {
                GeneratedDocumentEntity resumeEntity = new GeneratedDocumentEntity(finalResume);
                generatedDocumentRepository.save(resumeEntity);
                return resumeEntity.getId();
            });
            appContainer[0].setResumeVersionId(resumeEntityId);
            groundednessReport.put("resume", Map.of("passed", true, "documentId", resumeEntityId));
            auditTrail.add(timestamp() + " Tailored resume generated and verified successfully.");
        } catch (GroundednessException e) {
            groundednessPassed = false;
            groundednessReport.put("resume", Map.of("passed", false, "issues", e.getIssues()));
            auditTrail.add(timestamp() + " Resume generation failed groundedness: " + String.join(", ", e.getIssues()));
        } catch (Exception e) {
            groundednessPassed = false;
            groundednessReport.put("resume", Map.of("passed", false, "error", e.getMessage()));
            auditTrail.add(timestamp() + " Resume generation failed with unexpected error: " + e.getMessage());
        }

        try {
            coverLetterDoc = llmGenerationService.generate(applicationId, profileId, jobId, DocumentType.COVER_LETTER, jobEntity.getDescriptionClean());
            final GeneratedDocument finalCover = coverLetterDoc;
            UUID coverEntityId = transactionTemplate.execute(status -> {
                GeneratedDocumentEntity coverLetterEntity = new GeneratedDocumentEntity(finalCover);
                generatedDocumentRepository.save(coverLetterEntity);
                return coverLetterEntity.getId();
            });
            appContainer[0].setCoverLetterVersionId(coverEntityId);
            groundednessReport.put("coverLetter", Map.of("passed", true, "documentId", coverEntityId));
            auditTrail.add(timestamp() + " Tailored cover letter generated and verified successfully.");
        } catch (GroundednessException e) {
            groundednessPassed = false;
            groundednessReport.put("coverLetter", Map.of("passed", false, "issues", e.getIssues()));
            auditTrail.add(timestamp() + " Cover letter generation failed groundedness: " + String.join(", ", e.getIssues()));
        } catch (Exception e) {
            groundednessPassed = false;
            groundednessReport.put("coverLetter", Map.of("passed", false, "error", e.getMessage()));
            auditTrail.add(timestamp() + " Cover letter generation failed with unexpected error: " + e.getMessage());
        }

        final boolean finalGroundednessPassed = groundednessPassed;
        final Map<String, Object> finalGroundednessReport = groundednessReport;

        appContainer[0] = transactionTemplate.execute(status -> {
            appContainer[0].setGroundednessCheckPassed(finalGroundednessPassed);
            appContainer[0].setGroundednessReport(finalGroundednessReport);
            appContainer[0].setStatus(ApplicationStatus.VERIFYING.name());
            appContainer[0].setAuditTrail(new ArrayList<>(auditTrail));
            return applicationRepository.save(appContainer[0]);
        });

        // 5. Autonomy Policy decision
        MasterProfile profile = profileEntity.toDomain();
        Job job = jobEntity.toDomain();

        // Check circuit breaker state for scope (source/platform)
        boolean circuitBreakerOpen = circuitBreakerStateRepository.findByScope(jobEntity.getSource())
                .map(CircuitBreakerStateEntity::getStatus)
                .map(status -> status == CircuitBreakerStatus.OPEN)
                .orElse(false);

        // Count enqueued today
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        long submittedToday = applicationRepository.findAll().stream()
                .filter(app -> app.getProfileId().equals(profileId) && app.getCreatedAt() != null && app.getCreatedAt().isAfter(startOfDay))
                .filter(app -> {
                    String status = app.getStatus();
                    return ApplicationStatus.QUEUED.name().equals(status) ||
                           ApplicationStatus.READY.name().equals(status) ||
                           ApplicationStatus.SUBMITTED.name().equals(status);
                })
                .count();

        // Custom fields check (can be extended, defaults to false)
        boolean hasUnsupportedCustomFields = false;

        ApplicationCandidate candidate = new ApplicationCandidate(
                job,
                matchResult.score(),
                finalGroundednessPassed,
                hasUnsupportedCustomFields,
                circuitBreakerOpen,
                (int) submittedToday
        );

        AutonomyPolicy autonomyPolicy = new AutonomyPolicy();
        ApplicationDecision decision = autonomyPolicy.decide(profile, candidate);

        auditTrail.add(timestamp() + " Autonomy policy check: " + decision.reason());

        final GeneratedDocument finalResumeDoc = resumeDoc;
        final GeneratedDocument finalCoverDoc = coverLetterDoc;

        appContainer[0] = transactionTemplate.execute(status -> {
            appContainer[0].setAutomationTier(decision.tier().name());
            if (decision.shouldSubmit()) {
                appContainer[0].setStatus(ApplicationStatus.QUEUED.name());
                auditTrail.add(timestamp() + " Application approved for automation queue.");
                appContainer[0].setAuditTrail(new ArrayList<>(auditTrail));
                return applicationRepository.save(appContainer[0]);
            } else {
                UUID resumeId = appContainer[0].getResumeVersionId();
                UUID coverId = appContainer[0].getCoverLetterVersionId();
                
                // Unset FKs in application to avoid circular FK violations during deletion
                appContainer[0].setResumeVersionId(null);
                appContainer[0].setCoverLetterVersionId(null);
                applicationRepository.saveAndFlush(appContainer[0]);
                
                // Delete documents
                if (resumeId != null) {
                    generatedDocumentRepository.deleteById(resumeId);
                }
                if (coverId != null) {
                    generatedDocumentRepository.deleteById(coverId);
                }
                
                // Delete application
                applicationRepository.delete(appContainer[0]);
                applicationRepository.flush();
                
                // Set status on transient object to return
                appContainer[0].setStatus(ApplicationStatus.BLOCKED.name());
                auditTrail.add(timestamp() + " Application skipped/blocked: " + decision.reason());
                appContainer[0].setAuditTrail(new ArrayList<>(auditTrail));
                return appContainer[0];
            }
        });

        if (decision.shouldSubmit()) {
            if (killSwitchService.isHalted()) {
                appContainer[0] = transactionTemplate.execute(status -> {
                    appContainer[0].setStatus(ApplicationStatus.BLOCKED.name());
                    List<String> trail = appContainer[0].getAuditTrail();
                    trail.add(timestamp() + " Publish skipped: kill switch is active.");
                    appContainer[0].setAuditTrail(trail);
                    return applicationRepository.save(appContainer[0]);
                });
            } else {
                try {
                    String resumeIdStr = appContainer[0].getResumeVersionId() != null ? appContainer[0].getResumeVersionId().toString() : "";
                    String coverLetterIdStr = appContainer[0].getCoverLetterVersionId() != null ? appContainer[0].getCoverLetterVersionId().toString() : "";
                    
                    AutomationCommand command = new AutomationCommand(
                            applicationId.toString(),
                            jobEntity.getUrl(),
                            "false".equalsIgnoreCase(redisTemplate.opsForValue().get("cc:automation:shadow-mode")) ? "live" : "shadow",
                            profileId.toString(),
                            resumeIdStr,
                            finalResumeDoc != null ? finalResumeDoc.content() : "",
                            coverLetterIdStr,
                            finalCoverDoc != null ? finalCoverDoc.content() : "",
                            profile.name(),
                            profile.email(),
                            profile.phone(),
                            profile.linkedinUrl(),
                            profile.websiteUrl(),
                            "{}"
                    );
                    automationPublisher.publish(command);
                    appContainer[0] = transactionTemplate.execute(status -> {
                        List<String> trail = appContainer[0].getAuditTrail();
                        trail.add(timestamp() + " Dispatched to Redis automation stream.");
                        appContainer[0].setAuditTrail(trail);
                        return applicationRepository.save(appContainer[0]);
                    });
                } catch (Exception e) {
                    log.error("Failed to publish automation command to Redis", e);
                    appContainer[0] = transactionTemplate.execute(status -> {
                        appContainer[0].setStatus(ApplicationStatus.FAILED.name());
                        List<String> trail = appContainer[0].getAuditTrail();
                        trail.add(timestamp() + " Failed to dispatch to Redis stream: " + e.getMessage());
                        appContainer[0].setAuditTrail(trail);
                        return applicationRepository.save(appContainer[0]);
                    });
                }
            }
        }

        return appContainer[0];
    }

    private String timestamp() {
        return "[" + Instant.now().toString() + "]";
    }
}
