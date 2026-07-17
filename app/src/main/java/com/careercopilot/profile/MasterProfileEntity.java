package com.careercopilot.profile;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "master_profile")
public class MasterProfileEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "work_authorization", nullable = false)
    @Enumerated(EnumType.STRING)
    private WorkAuthorization workAuthorization;

    @Column(name = "visa_sponsorship_needed", nullable = false)
    private boolean visaSponsorshipNeeded;

    @Column(name = "salary_floor")
    private Integer salaryFloor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "locations", nullable = false, columnDefinition = "jsonb")
    private Set<String> locations = new HashSet<>();

    @Column(name = "remote_preference")
    private String remotePreference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocklist_companies", nullable = false, columnDefinition = "jsonb")
    private Set<String> blocklistCompanies = new HashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search_keywords", nullable = false, columnDefinition = "jsonb")
    private Set<String> searchKeywords = new HashSet<>();

    @Column(name = "daily_application_cap", nullable = false)
    private int dailyApplicationCap;

    @Column(name = "autonomy_threshold", nullable = false)
    private int autonomyThreshold;

    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    public MasterProfileEntity() {
    }

    public MasterProfileEntity(MasterProfile profile) {
        this.id = profile.id();
        this.userId = profile.userId();
        this.workAuthorization = profile.workAuthorization();
        this.visaSponsorshipNeeded = profile.visaSponsorshipNeeded();
        this.salaryFloor = profile.salaryFloor();
        this.locations = profile.locations();
        this.remotePreference = profile.remotePreference();
        this.blocklistCompanies = profile.blocklistCompanies();
        this.searchKeywords = profile.searchKeywords();
        this.dailyApplicationCap = profile.dailyApplicationCap();
        this.autonomyThreshold = profile.autonomyThreshold();
        this.name = profile.name();
        this.email = profile.email();
        this.phone = profile.phone();
        this.linkedinUrl = profile.linkedinUrl();
        this.websiteUrl = profile.websiteUrl();
    }

    public MasterProfile toDomain() {
        return new MasterProfile(
                this.id,
                this.userId,
                this.workAuthorization,
                this.visaSponsorshipNeeded,
                this.salaryFloor,
                this.locations,
                this.remotePreference,
                this.blocklistCompanies,
                this.dailyApplicationCap,
                this.autonomyThreshold,
                this.name,
                this.email,
                this.phone,
                this.linkedinUrl,
                this.websiteUrl,
                this.searchKeywords
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public WorkAuthorization getWorkAuthorization() {
        return workAuthorization;
    }

    public void setWorkAuthorization(WorkAuthorization workAuthorization) {
        this.workAuthorization = workAuthorization;
    }

    public boolean isVisaSponsorshipNeeded() {
        return visaSponsorshipNeeded;
    }

    public void setVisaSponsorshipNeeded(boolean visaSponsorshipNeeded) {
        this.visaSponsorshipNeeded = visaSponsorshipNeeded;
    }

    public Integer getSalaryFloor() {
        return salaryFloor;
    }

    public void setSalaryFloor(Integer salaryFloor) {
        this.salaryFloor = salaryFloor;
    }

    public Set<String> getLocations() {
        return locations;
    }

    public void setLocations(Set<String> locations) {
        this.locations = locations;
    }

    public String getRemotePreference() {
        return remotePreference;
    }

    public void setRemotePreference(String remotePreference) {
        this.remotePreference = remotePreference;
    }

    public Set<String> getBlocklistCompanies() {
        return blocklistCompanies;
    }

    public void setBlocklistCompanies(Set<String> blocklistCompanies) {
        this.blocklistCompanies = blocklistCompanies;
    }

    public int getDailyApplicationCap() {
        return dailyApplicationCap;
    }

    public void setDailyApplicationCap(int dailyApplicationCap) {
        this.dailyApplicationCap = dailyApplicationCap;
    }

    public int getAutonomyThreshold() {
        return autonomyThreshold;
    }

    public void setAutonomyThreshold(int autonomyThreshold) {
        this.autonomyThreshold = autonomyThreshold;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public Set<String> getSearchKeywords() { return searchKeywords; }
    public void setSearchKeywords(Set<String> searchKeywords) { this.searchKeywords = searchKeywords; }
}
