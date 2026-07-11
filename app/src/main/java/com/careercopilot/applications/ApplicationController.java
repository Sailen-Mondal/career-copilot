package com.careercopilot.applications;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** Returns all applications. */
    @GetMapping
    public ResponseEntity<List<ApplicationEntity>> listApplications() {
        return ResponseEntity.ok(applicationService.findAll());
    }

    /** Returns aggregate stats for the dashboard cards. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(applicationService.getStats());
    }

    /** Creates a new application. */
    @PostMapping
    public ResponseEntity<ApplicationEntity> create(@RequestBody CreateApplicationRequest request) {
        ApplicationEntity entity = applicationService.createApplication(
                request.jobId(),
                request.profileId(),
                request.matchScore(),
                request.breakdown()
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
            int matchScore,
            Map<String, Integer> breakdown
    ) {}

    /** Request body for updating status. */
    public record StatusUpdateRequest(String status) {}
}
