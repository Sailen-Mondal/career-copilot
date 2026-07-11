package com.careercopilot.profile;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "profile_fact")
public class ProfileFactEntity {

    @Id
    private UUID id;

    @Column(name = "master_profile_id", nullable = false)
    private UUID masterProfileId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FactType type;

    private String employer;

    private String title;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "bullet_text", nullable = false)
    private String bulletText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills", nullable = false, columnDefinition = "jsonb")
    private Set<String> skills = new HashSet<>();

    public ProfileFactEntity() {
    }

    public ProfileFactEntity(ProfileFact fact) {
        this.id = fact.id();
        this.masterProfileId = fact.masterProfileId();
        this.type = fact.type();
        this.employer = fact.employer();
        this.title = fact.title();
        this.startDate = fact.startDate();
        this.endDate = fact.endDate();
        this.bulletText = fact.bulletText();
        this.skills = fact.skills();
    }

    public ProfileFact toDomain() {
        return new ProfileFact(
                this.id,
                this.masterProfileId,
                this.type,
                this.employer,
                this.title,
                this.startDate,
                this.endDate,
                this.bulletText,
                this.skills
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMasterProfileId() {
        return masterProfileId;
    }

    public void setMasterProfileId(UUID masterProfileId) {
        this.masterProfileId = masterProfileId;
    }

    public FactType getType() {
        return type;
    }

    public void setType(FactType type) {
        this.type = type;
    }

    public String getEmployer() {
        return employer;
    }

    public void setEmployer(String employer) {
        this.employer = employer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getBulletText() {
        return bulletText;
    }

    public void setBulletText(String bulletText) {
        this.bulletText = bulletText;
    }

    public Set<String> getSkills() {
        return skills;
    }

    public void setSkills(Set<String> skills) {
        this.skills = skills;
    }
}
