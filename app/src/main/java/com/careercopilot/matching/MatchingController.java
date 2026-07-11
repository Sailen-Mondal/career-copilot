package com.careercopilot.matching;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing job-matching endpoints.
 *
 * <p>All routes require a valid {@code X-API-Key} header (enforced by
 * {@link com.careercopilot.shared.ApiKeyAuthFilter}).
 */
@RestController
@RequestMapping("/api/jobs")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /**
     * Scores a specific job against a candidate profile.
     *
     * @param jobId     the job to score
     * @param profileId the candidate profile to score against
     * @return a {@link MatchResult} with score, eligibility flag, and breakdown
     */
    @PostMapping("/{jobId}/match")
    public ResponseEntity<MatchResult> matchJob(
            @PathVariable UUID jobId,
            @RequestParam UUID profileId) {
        MatchResult result = matchingService.scoreJob(jobId, profileId);
        return ResponseEntity.ok(result);
    }
}
