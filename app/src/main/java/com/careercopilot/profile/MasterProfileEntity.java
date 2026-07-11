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

    @Column(name = "daily_application_cap", nullable = false)
    private int dailyApplicationCap;

    @Column(name = "autonomy_threshold", nullable = false)
    private int autonomyThreshold;

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
        this.dailyApplicationCap = profile.dailyApplicationCap();
        this.autonomyThreshold = profile.autonomyThreshold();
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
                this.autonomyThreshold
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
}
