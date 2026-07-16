package com.careercopilot.applications;

import com.careercopilot.generation.GeneratedDocumentRepository;
import com.careercopilot.shared.LlmClient;
import com.careercopilot.profile.MasterProfileRepository;
import com.careercopilot.profile.ProfileFactRepository;
import com.careercopilot.profile.MasterProfileEntity;
import com.careercopilot.profile.ProfileFactEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApplicationController.class);

    private final ApplicationService applicationService;
    private final ApplicationWorkflowService applicationWorkflowService;
    private final com.careercopilot.discovery.JobRepository jobRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationRepository applicationRepository;
    private final LlmClient llmClient;
    private final MasterProfileRepository masterProfileRepository;
    private final ProfileFactRepository profileFactRepository;
    private final ObjectMapper objectMapper;

    public ApplicationController(
            ApplicationService applicationService,
            ApplicationWorkflowService applicationWorkflowService,
            com.careercopilot.discovery.JobRepository jobRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            StringRedisTemplate redisTemplate,
            ApplicationRepository applicationRepository,
            LlmClient llmClient,
            MasterProfileRepository masterProfileRepository,
            ProfileFactRepository profileFactRepository,
            ObjectMapper objectMapper) {
        this.applicationService = applicationService;
        this.applicationWorkflowService = applicationWorkflowService;
        this.jobRepository = jobRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.redisTemplate = redisTemplate;
        this.applicationRepository = applicationRepository;
        this.llmClient = llmClient;
        this.masterProfileRepository = masterProfileRepository;
        this.profileFactRepository = profileFactRepository;
        this.objectMapper = objectMapper;
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
                    entity.getCreatedAt(),
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
                entity.getCreatedAt(),
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

    /** Submits the OTP verification code to Redis. */
    @PostMapping("/{id}/submit-otp")
    public ResponseEntity<Void> submitOtp(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing code");
        }
        redisTemplate.opsForValue().set("cc:otp:" + id, code, java.time.Duration.ofMinutes(5));
        log.info("Saved OTP code for application {} to Redis", id);
        return ResponseEntity.ok().build();
    }

    /** Transitions an application to a new status. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationEntity> updateStatus(
            @PathVariable UUID id,
            @RequestBody StatusUpdateRequest request) {
        ApplicationEntity entity = applicationService.transitionStatus(
                id, ApplicationStatus.valueOf(request.status().toUpperCase()));
        return ResponseEntity.ok(entity);
    }

    /** AI-driven Form-Filling Brain endpoint */
    @PostMapping("/{id}/fill")
    public ResponseEntity<Map<String, String>> fillFormFields(
            @PathVariable UUID id,
            @RequestBody List<FormFieldDto> fields) {
        log.info("Received AI form-filling request for application {} with {} fields", id, fields.size());
        
        ApplicationEntity app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found: " + id));
                
        com.careercopilot.discovery.JobEntity job = jobRepository.findById(app.getJobId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + app.getJobId()));

        MasterProfileEntity profile = masterProfileRepository.findById(app.getProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found: " + app.getProfileId()));

        List<ProfileFactEntity> facts = profileFactRepository.findByMasterProfileId(app.getProfileId());

        Map<String, String> resolved = new HashMap<>();
        List<FormFieldDto> aiRequiredFields = new ArrayList<>();

        // 1. Process with Standard Heuristics to guarantee 100% precision for personal info
        for (FormFieldDto field : fields) {
            String identifier = field.identifier();
            String labelText = field.labelText();
            String combined = (identifier + " " + labelText).toLowerCase();
            String type = field.type();

            if ("file".equalsIgnoreCase(type)) {
                if (combined.contains("resume") || combined.contains("cv")) {
                    resolved.put(identifier, "resume_" + id + ".pdf");
                } else if (combined.contains("cover")) {
                    resolved.put(identifier, "cover_letter_" + id + ".txt");
                }
            } else if (combined.contains("first name") || combined.contains("firstname")) {
                resolved.put(identifier, getFirstName(profile.getName()));
            } else if (combined.contains("last name") || combined.contains("lastname")) {
                resolved.put(identifier, getLastName(profile.getName()));
            } else if (combined.contains("full name") || combined.contains("fullname") || (combined.contains("name") && !combined.contains("company") && !combined.contains("school") && !combined.contains("university"))) {
                resolved.put(identifier, profile.getName());
            } else if (combined.contains("email")) {
                resolved.put(identifier, profile.getEmail());
            } else if (combined.contains("phone") || combined.contains("mobile") || combined.contains("tel")) {
                resolved.put(identifier, profile.getPhone());
            } else if (combined.contains("linkedin")) {
                resolved.put(identifier, profile.getLinkedinUrl());
            } else if (combined.contains("website") || combined.contains("portfolio") || combined.contains("github") || combined.contains("url")) {
                resolved.put(identifier, profile.getWebsiteUrl());
            } else if (combined.contains("gender")) {
                resolved.put(identifier, "Male");
            } else if (combined.contains("pronouns")) {
                resolved.put(identifier, "He/Him");
            } else if (combined.contains("veteran")) {
                resolved.put(identifier, "No");
            } else if (combined.contains("disability")) {
                resolved.put(identifier, "No");
            } else if (combined.contains("hispanic") || combined.contains("latino")) {
                resolved.put(identifier, "No");
            } else {
                aiRequiredFields.add(field);
            }
        }

        // 2. Process remaining fields using the AI Brain
        if (!aiRequiredFields.isEmpty()) {
            try {
                String systemPrompt = """
                        You are the form-filling brain for a job application automation system.
                        Your job is to determine the best answers or select options for a list of form fields based on the candidate's profile facts.
                        Rules:
                        1. For text/textarea fields, write professional, concise answers (under 80 words) based strictly on candidate facts. Do not exaggerate or hallucinate.
                        2. For select/dropdown or checkbox/radio options, you MUST choose the option text from the provided options list that best fits the candidate.
                        3. If a question is about work authorization/sponsorship, answer "Yes" to authorized and "Yes" to require sponsorship (unless facts state otherwise).
                        4. Output ONLY a clean, raw JSON object mapping the field's 'identifier' to the chosen answer text or option.
                        5. Do NOT wrap the JSON in markdown code blocks like ```json ... ```. Output raw JSON text.
                        """;

                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append("CANDIDATE FACTS:\n");
                for (ProfileFactEntity fact : facts) {
                    userPrompt.append("- ").append(fact.getBulletText()).append("\n");
                }
                userPrompt.append("\nCOMPANY: ").append(job.getCompany());
                userPrompt.append("\nJOB ROLE: ").append(job.getTitle());
                userPrompt.append("\n\nFIELDS TO RESOLVE:\n");

                for (FormFieldDto field : aiRequiredFields) {
                    userPrompt.append("{\n");
                    userPrompt.append("  \"identifier\": \"").append(field.identifier()).append("\",\n");
                    userPrompt.append("  \"labelText\": \"").append(field.labelText()).append("\",\n");
                    userPrompt.append("  \"type\": \"").append(field.type()).append("\",\n");
                    if (field.options() != null && !field.options().isEmpty()) {
                        userPrompt.append("  \"options\": ").append(objectMapper.writeValueAsString(field.options())).append(",\n");
                    }
                    userPrompt.append("  \"required\": ").append(field.required()).append("\n");
                    userPrompt.append("}\n");
                }

                String response = llmClient.generate(systemPrompt, userPrompt.toString());
                response = response.trim();
                if (response.startsWith("```")) {
                    if (response.startsWith("```json")) {
                        response = response.substring(7);
                    } else {
                        response = response.substring(3);
                    }
                    if (response.endsWith("```")) {
                        response = response.substring(0, response.length() - 3);
                    }
                    response = response.trim();
                }

                Map<String, String> aiAnswers = objectMapper.readValue(response, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                if (aiAnswers != null) {
                    resolved.putAll(aiAnswers);
                }
            } catch (Exception e) {
                log.error("Failed to generate AI form-filling values", e);
            }
        }

        return ResponseEntity.ok(resolved);
    }

    private String getFirstName(String fullName) {
        if (fullName == null) return "Candidate";
        String[] parts = fullName.split("\\s+");
        return parts[0];
    }

    private String getLastName(String fullName) {
        if (fullName == null) return "Name";
        String[] parts = fullName.split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : "Name";
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
