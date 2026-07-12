package com.careercopilot.applications;

import com.careercopilot.discovery.JobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages application lifecycle: creation, status transitions, audit logging,
 * and aggregate statistics for the dashboard.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                              JobRepository jobRepository) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Creates a new application with status QUEUED.
     */
    @Transactional
    public ApplicationEntity createApplication(UUID jobId,
                                                UUID profileId,
                                                int matchScore,
                                                Map<String, Integer> breakdown) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(UUID.randomUUID());
        entity.setJobId(jobId);
        entity.setProfileId(profileId);
        entity.setMatchScore(matchScore);
        entity.setMatchScoreBreakdown(breakdown != null ? breakdown : Map.of());
        entity.setAutomationTier(AutomationTier.AUTO.name());
        entity.setStatus(ApplicationStatus.QUEUED.name());
        entity.setGroundednessCheckPassed(false);
        entity.setGroundednessReport(Map.of());
        entity.setCreatedAt(Instant.now());
        entity.setAuditTrail(new ArrayList<>(List.of(
                timestamp() + " Application created with match score " + matchScore
        )));
        return applicationRepository.save(entity);
    }

    /**
     * Transitions an application to a new status and appends an audit entry.
     */
    @Transactional
    public ApplicationEntity transitionStatus(UUID applicationId, ApplicationStatus newStatus) {
        ApplicationEntity entity = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Application not found: " + applicationId));
        String oldStatus = entity.getStatus();
        entity.setStatus(newStatus.name());
        if (newStatus == ApplicationStatus.SUBMITTED) {
            entity.setSubmittedAt(Instant.now());
        }
        appendAuditInternal(entity, "Status transition: " + oldStatus + " → " + newStatus.name());
        return applicationRepository.save(entity);
    }

    /**
     * Appends an audit trail entry to an existing application.
     */
    @Transactional
    public void appendAuditEntry(UUID applicationId, String entry) {
        ApplicationEntity entity = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Application not found: " + applicationId));
        appendAuditInternal(entity, entry);
        applicationRepository.save(entity);
    }

    /**
     * Returns aggregate statistics for the dashboard.
     */
    public Map<String, Object> getStats() {
        long totalDiscovered = jobRepository.count();
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        long appliedToday = applicationRepository.countBySubmittedAtAfter(startOfDay);

        List<ApplicationEntity> all = applicationRepository.findAll();
        double avgMatchScore = all.stream()
                .mapToInt(ApplicationEntity::getMatchScore)
                .average()
                .orElse(0.0);

        long submitted = all.stream()
                .filter(a -> ApplicationStatus.SUBMITTED.name().equals(a.getStatus()))
                .count();
        double successRate = all.isEmpty() ? 0.0
                : (double) submitted / all.size() * 100.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDiscovered", totalDiscovered);
        stats.put("appliedToday", appliedToday);
        stats.put("avgMatchScore", Math.round(avgMatchScore));
        stats.put("successRate", Math.round(successRate));
        return stats;
    }

    /**
     * Returns all applications ordered by newest first.
     */
    public List<ApplicationEntity> findAll() {
        return applicationRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .collect(java.util.stream.Collectors.toList());
    }

    // ---- Private helpers ----

    private void appendAuditInternal(ApplicationEntity entity, String entry) {
        List<String> trail = entity.getAuditTrail();
        if (trail == null) {
            trail = new ArrayList<>();
        }
        trail.add(timestamp() + " " + entry);
        entity.setAuditTrail(trail);
    }

    private String timestamp() {
        return "[" + Instant.now().toString() + "]";
    }
}
