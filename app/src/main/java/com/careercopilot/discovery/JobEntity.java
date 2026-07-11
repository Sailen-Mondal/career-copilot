package com.careercopilot.discovery;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "job")
public class JobEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    private String location;

    @Column(name = "location_type")
    private String locationType;

    @Column(name = "description_raw", columnDefinition = "TEXT")
    private String descriptionRaw;

    @Column(name = "description_clean", columnDefinition = "TEXT")
    private String descriptionClean;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills_required", nullable = false, columnDefinition = "jsonb")
    private Set<String> requiredSkills;

    private String seniority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "salary_range", columnDefinition = "jsonb")
    private SalaryRange salaryRange;

    @Column(name = "work_auth_required")
    private String workAuthRequired;

    @Column(name = "sponsorship_available")
    private boolean sponsorshipAvailable;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "scraped_at", nullable = false)
    private Instant scrapedAt = Instant.now();

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.ACTIVE;

    @Column(name = "dedup_key", unique = true)
    private String dedupKey;

    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "embedding_vector", columnDefinition = "vector(1536)")
    private Object embeddingVector;

    public JobEntity() {
    }

    public JobEntity(Job job) {
        this.id = job.id();
        this.source = job.source();
        this.externalId = job.externalId();
        this.url = job.url() != null ? job.url().toString() : null;
        this.company = job.company();
        this.title = job.title();
        this.location = job.location();
        this.locationType = job.locationType();
        this.descriptionRaw = job.descriptionRaw();
        this.descriptionClean = job.descriptionClean();
        this.requiredSkills = job.requiredSkills();
        this.seniority = job.seniority();
        this.salaryRange = job.salaryRange();
        this.workAuthRequired = job.workAuthRequired();
        this.sponsorshipAvailable = job.sponsorshipAvailable();
        this.postedAt = job.postedAt();
        if (job.scrapedAt() != null) {
            this.scrapedAt = job.scrapedAt();
        }
        this.lastVerifiedAt = job.lastVerifiedAt();
        this.status = job.status() != null ? job.status() : JobStatus.ACTIVE;
        this.dedupKey = job.dedupKey();
        this.embeddingVector = serializeVector(job.embeddingVector());
    }

    public Job toDomain() {
        return new Job(
                this.id,
                this.source,
                this.externalId,
                this.url != null ? URI.create(this.url) : null,
                this.company,
                this.title,
                this.location,
                this.locationType,
                this.descriptionRaw,
                this.descriptionClean,
                this.requiredSkills,
                this.seniority,
                this.salaryRange,
                this.workAuthRequired,
                this.sponsorshipAvailable,
                this.postedAt,
                this.scrapedAt,
                this.lastVerifiedAt,
                this.status,
                this.dedupKey,
                deserializeVector(this.embeddingVector)
        );
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public String getDescriptionRaw() {
        return descriptionRaw;
    }

    public void setDescriptionRaw(String descriptionRaw) {
        this.descriptionRaw = descriptionRaw;
    }

    public String getDescriptionClean() {
        return descriptionClean;
    }

    public void setDescriptionClean(String descriptionClean) {
        this.descriptionClean = descriptionClean;
    }

    public Set<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(Set<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public String getSeniority() {
        return seniority;
    }

    public void setSeniority(String seniority) {
        this.seniority = seniority;
    }

    public SalaryRange getSalaryRange() {
        return salaryRange;
    }

    public void setSalaryRange(SalaryRange salaryRange) {
        this.salaryRange = salaryRange;
    }

    public String getWorkAuthRequired() {
        return workAuthRequired;
    }

    public void setWorkAuthRequired(String workAuthRequired) {
        this.workAuthRequired = workAuthRequired;
    }

    public boolean isSponsorshipAvailable() {
        return sponsorshipAvailable;
    }

    public void setSponsorshipAvailable(boolean sponsorshipAvailable) {
        this.sponsorshipAvailable = sponsorshipAvailable;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Instant getScrapedAt() {
        return scrapedAt;
    }

    public void setScrapedAt(Instant scrapedAt) {
        this.scrapedAt = scrapedAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public static String serializeVectorToString(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static Object serializeVector(float[] vector) {
        if (vector == null) {
            return null;
        }
        try {
            org.postgresql.util.PGobject pgObject = new org.postgresql.util.PGobject();
            pgObject.setType("vector");
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < vector.length; i++) {
                sb.append(vector[i]);
                if (i < vector.length - 1) {
                    sb.append(",");
                }
            }
            sb.append("]");
            pgObject.setValue(sb.toString());
            return pgObject;
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to serialize vector", e);
        }
    }

    public static float[] deserializeVector(Object dbData) {
        if (dbData == null) {
            return null;
        }
        String value;
        if (dbData instanceof org.postgresql.util.PGobject pgObject) {
            value = pgObject.getValue();
        } else {
            value = dbData.toString();
        }
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty()) {
            return new float[0];
        }
        String[] parts = value.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    public float[] getEmbeddingVector() {
        return deserializeVector(this.embeddingVector);
    }

    public void setEmbeddingVector(float[] embeddingVector) {
        this.embeddingVector = serializeVector(embeddingVector);
    }
}
