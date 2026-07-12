package com.careercopilot.applications;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity representing a job application in the {@code application} table.
 *
 * <p>Schema is owned by Flyway (V1 + V3). Hibernate is in {@code validate} mode.
 */
@Entity
@Table(name = "application")
public class ApplicationEntity {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    /** Added in V3 migration. */
    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "resume_version_id")
    private UUID resumeVersionId;

    @Column(name = "cover_letter_version_id")
    private UUID coverLetterVersionId;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "match_score_breakdown", nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> matchScoreBreakdown;

    @Column(name = "automation_tier", nullable = false)
    private String automationTier;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "platform")
    private String platform;

    @Column(name = "external_application_id")
    private String externalApplicationId;

    @Column(name = "groundedness_check_passed", nullable = false)
    private boolean groundednessCheckPassed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "groundedness_report", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> groundednessReport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_trail", nullable = false, columnDefinition = "jsonb")
    private List<String> auditTrail = new ArrayList<>();

    public ApplicationEntity() {
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public UUID getProfileId() { return profileId; }
    public void setProfileId(UUID profileId) { this.profileId = profileId; }

    public UUID getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(UUID resumeVersionId) { this.resumeVersionId = resumeVersionId; }

    public UUID getCoverLetterVersionId() { return coverLetterVersionId; }
    public void setCoverLetterVersionId(UUID coverLetterVersionId) { this.coverLetterVersionId = coverLetterVersionId; }

    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }

    public Map<String, Integer> getMatchScoreBreakdown() { return matchScoreBreakdown; }
    public void setMatchScoreBreakdown(Map<String, Integer> matchScoreBreakdown) { this.matchScoreBreakdown = matchScoreBreakdown; }

    public String getAutomationTier() { return automationTier; }
    public void setAutomationTier(String automationTier) { this.automationTier = automationTier; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getExternalApplicationId() { return externalApplicationId; }
    public void setExternalApplicationId(String externalApplicationId) { this.externalApplicationId = externalApplicationId; }

    public boolean isGroundednessCheckPassed() { return groundednessCheckPassed; }
    public void setGroundednessCheckPassed(boolean groundednessCheckPassed) { this.groundednessCheckPassed = groundednessCheckPassed; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> getGroundednessReport() { return groundednessReport; }
    public void setGroundednessReport(Map<String, Object> groundednessReport) { this.groundednessReport = groundednessReport; }

    public List<String> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<String> auditTrail) { this.auditTrail = auditTrail; }
}
