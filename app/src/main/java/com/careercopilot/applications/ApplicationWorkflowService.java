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
import org.springframework.transaction.annotation.Transactional;
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

    public ApplicationWorkflowService(
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            MasterProfileRepository masterProfileRepository,
            MatchingService matchingService,
            LlmGenerationService llmGenerationService,
            GeneratedDocumentRepository generatedDocumentRepository,
            CircuitBreakerStateRepository circuitBreakerStateRepository,
            AutomationPublisher automationPublisher,
            KillSwitchService killSwitchService) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.masterProfileRepository = masterProfileRepository;
        this.matchingService = matchingService;
        this.llmGenerationService = llmGenerationService;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.circuitBreakerStateRepository = circuitBreakerStateRepository;
        this.automationPublisher = automationPublisher;
        this.killSwitchService = killSwitchService;
    }

    @Transactional
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
        ApplicationEntity application = new ApplicationEntity();
        UUID applicationId = UUID.randomUUID();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setProfileId(profileId);
        application.setMatchScore(matchResult.score());
        application.setMatchScoreBreakdown(matchResult.breakdown());
        application.setStatus(ApplicationStatus.GENERATING.name());
        application.setAutomationTier(AutomationTier.AUTO.name());
        application.setGroundednessCheckPassed(false);
        application.setGroundednessReport(Map.of());
        
        List<String> auditTrail = new ArrayList<>();
        auditTrail.add(timestamp() + " Workflow started. Match score computed: " + matchResult.score());
        if (!matchResult.eligible()) {
            auditTrail.add(timestamp() + " Matcher marked job ineligible: " + matchResult.ineligibilityReason());
        }
        application.setAuditTrail(auditTrail);
        application = applicationRepository.save(application);

        // 4. Generate documents and check groundedness
        boolean groundednessPassed = true;
        Map<String, Object> groundednessReport = new HashMap<>();
        GeneratedDocument resumeDoc = null;
        GeneratedDocument coverLetterDoc = null;

        // Verify if candidate has facts at all
        try {
            resumeDoc = llmGenerationService.generate(applicationId, profileId, jobId, DocumentType.RESUME, jobEntity.getDescriptionClean());
            GeneratedDocumentEntity resumeEntity = new GeneratedDocumentEntity(resumeDoc);
            generatedDocumentRepository.save(resumeEntity);
            application.setResumeVersionId(resumeEntity.getId());
            groundednessReport.put("resume", Map.of("passed", true, "documentId", resumeEntity.getId()));
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
            GeneratedDocumentEntity coverLetterEntity = new GeneratedDocumentEntity(coverLetterDoc);
            generatedDocumentRepository.save(coverLetterEntity);
            application.setCoverLetterVersionId(coverLetterEntity.getId());
            groundednessReport.put("coverLetter", Map.of("passed", true, "documentId", coverLetterEntity.getId()));
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

        application.setGroundednessCheckPassed(groundednessPassed);
        application.setGroundednessReport(groundednessReport);

        // Update status to VERIFYING during policy decision
        application.setStatus(ApplicationStatus.VERIFYING.name());
        applicationRepository.save(application);

        // 5. Autonomy Policy decision
        MasterProfile profile = profileEntity.toDomain();
        Job job = jobEntity.toDomain();

        // Check circuit breaker state for scope (source/platform)
        boolean circuitBreakerOpen = circuitBreakerStateRepository.findByScope(jobEntity.getSource())
                .map(CircuitBreakerStateEntity::getStatus)
                .map(status -> status == CircuitBreakerStatus.OPEN)
                .orElse(false);

        // Count submitted today
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        int submittedToday = (int) applicationRepository.countBySubmittedAtAfter(startOfDay);

        // Custom fields check (can be extended, defaults to false)
        boolean hasUnsupportedCustomFields = false;

        ApplicationCandidate candidate = new ApplicationCandidate(
                job,
                matchResult.score(),
                groundednessPassed,
                hasUnsupportedCustomFields,
                circuitBreakerOpen,
                submittedToday
        );

        AutonomyPolicy autonomyPolicy = new AutonomyPolicy();
        ApplicationDecision decision = autonomyPolicy.decide(profile, candidate);

        application.setAutomationTier(decision.tier().name());
        auditTrail.add(timestamp() + " Autonomy policy check: " + decision.reason());

        if (decision.shouldSubmit()) {
            application.setStatus(ApplicationStatus.QUEUED.name());
            auditTrail.add(timestamp() + " Application approved for automation queue.");
            applicationRepository.save(application);

            // 6. Redis Queue Publishing (if kill switch is not active)
            if (killSwitchService.isHalted()) {
                application.setStatus(ApplicationStatus.BLOCKED.name());
                auditTrail.add(timestamp() + " Publish skipped: kill switch is active.");
                applicationRepository.save(application);
            } else {
                try {
                    String resumeIdStr = application.getResumeVersionId() != null ? application.getResumeVersionId().toString() : "";
                    String coverLetterIdStr = application.getCoverLetterVersionId() != null ? application.getCoverLetterVersionId().toString() : "";
                    
                    AutomationCommand command = new AutomationCommand(
                            applicationId.toString(),
                            jobEntity.getUrl(),
                            "shadow",
                            profileId.toString(),
                            resumeIdStr,
                            resumeDoc != null ? resumeDoc.content() : "",
                            coverLetterIdStr,
                            coverLetterDoc != null ? coverLetterDoc.content() : "",
                            profile.name(),
                            profile.email(),
                            profile.phone(),
                            profile.linkedinUrl(),
                            profile.websiteUrl()
                    );
                    automationPublisher.publish(command);
                    auditTrail.add(timestamp() + " Dispatched to Redis automation stream.");
                } catch (Exception e) {
                    log.error("Failed to publish automation command to Redis", e);
                    application.setStatus(ApplicationStatus.FAILED.name());
                    auditTrail.add(timestamp() + " Failed to dispatch to Redis stream: " + e.getMessage());
                }
            }
        } else {
            // Skipped or blocked by autonomy policy
            application.setStatus(ApplicationStatus.BLOCKED.name());
            auditTrail.add(timestamp() + " Application skipped/blocked: " + decision.reason());
        }

        return applicationRepository.save(application);
    }

    private String timestamp() {
        return "[" + Instant.now().toString() + "]";
    }
}
