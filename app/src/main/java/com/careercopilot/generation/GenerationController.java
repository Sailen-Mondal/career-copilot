package com.careercopilot.generation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for document generation endpoints.
 *
 * <p>All routes require a valid {@code X-API-Key} header (enforced by
 * {@link com.careercopilot.shared.ApiKeyAuthFilter}).
 */
@RestController
@RequestMapping("/api/applications")
public class GenerationController {

    private final LlmGenerationService generationService;

    public GenerationController(LlmGenerationService generationService) {
        this.generationService = generationService;
    }

    /**
     * Generates a resume or cover letter for the given application.
     *
     * @param applicationId path variable — the application to generate a document for
     * @param request       body containing profileId, jobId, documentType, and jobDescriptionClean
     * @return 200 with the generated document, or 422 with issues if groundedness fails
     */
    @PostMapping("/{applicationId}/generate")
    public ResponseEntity<?> generate(
            @PathVariable UUID applicationId,
            @RequestBody GenerateRequest request) {
        try {
            GeneratedDocument doc = generationService.generate(
                    applicationId,
                    request.profileId(),
                    request.jobId(),
                    request.documentType(),
                    request.jobDescriptionClean()
            );
            return ResponseEntity.ok(doc);
        } catch (GroundednessException ex) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error", "Groundedness check failed",
                            "issues", ex.getIssues()
                    ));
        }
    }

    /** Request body for the generate endpoint. */
    public record GenerateRequest(
            UUID profileId,
            UUID jobId,
            DocumentType documentType,
            String jobDescriptionClean
    ) {}
}
