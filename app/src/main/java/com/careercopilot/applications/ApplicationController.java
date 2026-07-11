package com.careercopilot.applications;

import com.careercopilot.generation.GeneratedDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for application CRUD, status transitions, and dashboard stats.
 *
 * <p>All routes require a valid {@code X-API-Key} header.
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ApplicationWorkflowService applicationWorkflowService;
    private final com.careercopilot.discovery.JobRepository jobRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;

    public ApplicationController(
            ApplicationService applicationService,
            ApplicationWorkflowService applicationWorkflowService,
            com.careercopilot.discovery.JobRepository jobRepository,
            GeneratedDocumentRepository generatedDocumentRepository) {
        this.applicationService = applicationService;
        this.applicationWorkflowService = applicationWorkflowService;
        this.jobRepository = jobRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
    }

    /** Returns all applications joined with job info as DTOs (without document content for speed). */
    @GetMapping
    public ResponseEntity<List<ApplicationDto>> listApplications() {
        List<ApplicationEntity> entities = applicationService.findAll();
        List<ApplicationDto> dtos = entities.stream().map(entity -> {
            var jobOpt = jobRepository.findById(entity.getJobId());
            String company = jobOpt.map(com.careercopilot.discovery.JobEntity::getCompany).orElse("—");
            String title = jobOpt.map(com.careercopilot.discovery.JobEntity::getTitle).orElse("—");
            String url = jobOpt.map(com.careercopilot.discovery.JobEntity::getUrl).orElse("—");
            String source = jobOpt.map(com.careercopilot.discovery.JobEntity::getSource).orElse("—");

            // Extract reason from audit log
            String reason = "";
            if (entity.getAuditTrail() != null && !entity.getAuditTrail().isEmpty()) {
                for (String entry : entity.getAuditTrail()) {
                    if (entry.contains("skipped/blocked") || entry.contains("failed") || entry.contains("failed groundedness")) {
                        int bracketIdx = entry.indexOf("]");
                        reason = bracketIdx != -1 ? entry.substring(bracketIdx + 1).trim() : entry;
                    }
                }
                if (reason.isEmpty() && (ApplicationStatus.BLOCKED.name().equalsIgnoreCase(entity.getStatus()) || ApplicationStatus.FAILED.name().equalsIgnoreCase(entity.getStatus()))) {
                    String last = entity.getAuditTrail().get(entity.getAuditTrail().size() - 1);
                    int bracketIdx = last.indexOf("]");
                    reason = bracketIdx != -1 ? last.substring(bracketIdx + 1).trim() : last;
                }
            }

            return new ApplicationDto(
                    entity.getId(),
                    entity.getJobId(),
                    company,
                    title,
                    url,
                    entity.getProfileId(),
                    entity.getResumeVersionId(),
                    entity.getCoverLetterVersionId(),
                    entity.getMatchScore(),
                    entity.getMatchScoreBreakdown(),
                    entity.getAutomationTier(),
                    entity.getStatus().toLowerCase(), // Lowercase to match frontend CSS status classes
                    entity.getSubmittedAt(),
                    source,
                    entity.getExternalApplicationId(),
                    entity.isGroundednessCheckPassed(),
                    entity.getGroundednessReport(),
                    entity.getAuditTrail(),
                    reason,
                    null,
                    null
            );
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    /** Returns the full application detail, including generated document contents. */
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDto> getApplicationById(@PathVariable UUID id) {
        ApplicationEntity entity = applicationService.findAll().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found: " + id));

        var jobOpt = jobRepository.findById(entity.getJobId());
        String company = jobOpt.map(com.careercopilot.discovery.JobEntity::getCompany).orElse("—");
        String title = jobOpt.map(com.careercopilot.discovery.JobEntity::getTitle).orElse("—");
        String url = jobOpt.map(com.careercopilot.discovery.JobEntity::getUrl).orElse("—");
        String source = jobOpt.map(com.careercopilot.discovery.JobEntity::getSource).orElse("—");

        // Extract reason from audit log
        String reason = "";
        if (entity.getAuditTrail() != null && !entity.getAuditTrail().isEmpty()) {
            for (String entry : entity.getAuditTrail()) {
                if (entry.contains("skipped/blocked") || entry.contains("failed") || entry.contains("failed groundedness")) {
                    int bracketIdx = entry.indexOf("]");
                    reason = bracketIdx != -1 ? entry.substring(bracketIdx + 1).trim() : entry;
                }
            }
            if (reason.isEmpty() && (ApplicationStatus.BLOCKED.name().equalsIgnoreCase(entity.getStatus()) || ApplicationStatus.FAILED.name().equalsIgnoreCase(entity.getStatus()))) {
                String last = entity.getAuditTrail().get(entity.getAuditTrail().size() - 1);
                int bracketIdx = last.indexOf("]");
                reason = bracketIdx != -1 ? last.substring(bracketIdx + 1).trim() : last;
            }
        }

        String resumeContent = null;
        if (entity.getResumeVersionId() != null) {
            resumeContent = generatedDocumentRepository.findById(entity.getResumeVersionId())
                    .map(com.careercopilot.generation.GeneratedDocumentEntity::getContent)
                    .orElse(null);
        }

        String coverLetterContent = null;
        if (entity.getCoverLetterVersionId() != null) {
            coverLetterContent = generatedDocumentRepository.findById(entity.getCoverLetterVersionId())
                    .map(com.careercopilot.generation.GeneratedDocumentEntity::getContent)
                    .orElse(null);
        }

        ApplicationDto dto = new ApplicationDto(
                entity.getId(),
                entity.getJobId(),
                company,
                title,
                url,
                entity.getProfileId(),
                entity.getResumeVersionId(),
                entity.getCoverLetterVersionId(),
                entity.getMatchScore(),
                entity.getMatchScoreBreakdown(),
                entity.getAutomationTier(),
                entity.getStatus().toLowerCase(),
                entity.getSubmittedAt(),
                source,
                entity.getExternalApplicationId(),
                entity.isGroundednessCheckPassed(),
                entity.getGroundednessReport(),
                entity.getAuditTrail(),
                reason,
                resumeContent,
                coverLetterContent
        );
        return ResponseEntity.ok(dto);
    }

    /** Returns aggregate stats for the dashboard cards. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(applicationService.getStats());
    }

    /** Creates a new application using the backend-driven workflow. */
    @PostMapping
    public ResponseEntity<ApplicationEntity> create(@RequestBody CreateApplicationRequest request) {
        ApplicationEntity entity = applicationWorkflowService.runWorkflow(
                request.jobId(),
                request.profileId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(entity);
    }

    /** Transitions an application to a new status. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationEntity> updateStatus(
            @PathVariable UUID id,
            @RequestBody StatusUpdateRequest request) {
        ApplicationEntity entity = applicationService.transitionStatus(
                id, ApplicationStatus.valueOf(request.status()));
        return ResponseEntity.ok(entity);
    }

    /** Request body for creating an application. */
    public record CreateApplicationRequest(
            UUID jobId,
            UUID profileId,
            Integer matchScore,
            Map<String, Integer> breakdown
    ) {}

    /** Request body for updating status. */
    public record StatusUpdateRequest(String status) {}
}
