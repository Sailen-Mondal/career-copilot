package com.careercopilot.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final String DEFAULT_USER_ID = "default-user";

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<MasterProfile> getProfile() {
        return profileService.getProfileByUserId(DEFAULT_USER_ID)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MasterProfile> createOrUpdateProfile(@RequestBody CreateProfileRequest request) {
        if (request.dailyApplicationCap() < 0) {
            return ResponseEntity.badRequest().build();
        }
        if (request.autonomyThreshold() < 0 || request.autonomyThreshold() > 100) {
            return ResponseEntity.badRequest().build();
        }

        Optional<MasterProfile> existing = profileService.getProfileByUserId(DEFAULT_USER_ID);
        UUID id = existing.map(MasterProfile::id).orElseGet(UUID::randomUUID);
        MasterProfile profile = new MasterProfile(
                id,
                DEFAULT_USER_ID,
                request.workAuthorization(),
                request.visaSponsorshipNeeded(),
                request.salaryFloor(),
                request.locations(),
                request.remotePreference(),
                request.blocklistCompanies(),
                request.dailyApplicationCap(),
                request.autonomyThreshold(),
                request.name(),
                request.email(),
                request.phone(),
                request.linkedinUrl(),
                request.websiteUrl()
        );
        MasterProfile saved = profileService.saveProfile(DEFAULT_USER_ID, profile);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/facts")
    public ResponseEntity<List<ProfileFact>> getFacts() {
        Optional<MasterProfile> profileOpt = profileService.getProfileByUserId(DEFAULT_USER_ID);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<ProfileFact> facts = profileService.getFactsByMasterProfileId(profileOpt.get().id());
        return ResponseEntity.ok(facts);
    }

    @PostMapping("/facts")
    public ResponseEntity<ProfileFact> addFact(@RequestBody CreateFactRequest request) {
        MasterProfile profile = profileService.getProfileByUserId(DEFAULT_USER_ID)
                .orElseGet(() -> {
                    UUID profileId = UUID.randomUUID();
                    MasterProfile newProfile = new MasterProfile(
                            profileId,
                            DEFAULT_USER_ID,
                            WorkAuthorization.OTHER,
                            false,
                            null,
                            Set.of(),
                            null,
                            Set.of(),
                            3,
                            85,
                            null,
                            null,
                            null,
                            null,
                            null
                    );
                    return profileService.saveProfile(DEFAULT_USER_ID, newProfile);
                });

        UUID factId = UUID.randomUUID();
        ProfileFact fact = new ProfileFact(
                factId,
                profile.id(),
                request.type(),
                request.employer(),
                request.title(),
                request.startDate(),
                request.endDate(),
                request.bulletText(),
                request.skills()
        );
        ProfileFact saved = profileService.addFact(fact);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/facts/{id}")
    public ResponseEntity<ProfileFact> updateFact(@PathVariable("id") UUID id, @RequestBody CreateFactRequest request) {
        MasterProfile profile = profileService.getProfileByUserId(DEFAULT_USER_ID)
                .orElseThrow(() -> new IllegalArgumentException("Master profile not found"));

        ProfileFact factUpdate = new ProfileFact(
                id,
                profile.id(),
                request.type(),
                request.employer(),
                request.title(),
                request.startDate(),
                request.endDate(),
                request.bulletText(),
                request.skills()
        );

        try {
            ProfileFact updated = profileService.updateFact(id, factUpdate);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/facts/{id}")
    public ResponseEntity<Void> deleteFact(@PathVariable("id") UUID id) {
        try {
            profileService.deleteFact(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/default/autonomy-threshold")
    public ResponseEntity<Map<String, Object>> updateAutonomyThreshold(@RequestBody Map<String, Integer> request) {
        Integer threshold = request.get("threshold");
        if (threshold == null || threshold < 0 || threshold > 100) {
            return ResponseEntity.badRequest().build();
        }
        Optional<MasterProfile> profileOpt = profileService.getProfileByUserId(DEFAULT_USER_ID);
        MasterProfile old;
        if (profileOpt.isEmpty()) {
            UUID profileId = UUID.randomUUID();
            old = new MasterProfile(
                    profileId,
                    DEFAULT_USER_ID,
                    WorkAuthorization.OTHER,
                    false,
                    null,
                    java.util.Set.of(),
                    null,
                    java.util.Set.of(),
                    3,
                    threshold,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } else {
            old = profileOpt.get();
        }
        MasterProfile updated = new MasterProfile(
                old.id(),
                old.userId(),
                old.workAuthorization(),
                old.visaSponsorshipNeeded(),
                old.salaryFloor(),
                old.locations(),
                old.remotePreference(),
                old.blocklistCompanies(),
                old.dailyApplicationCap(),
                threshold,
                old.name(),
                old.email(),
                old.phone(),
                old.linkedinUrl(),
                old.websiteUrl()
        );
        profileService.saveProfile(DEFAULT_USER_ID, updated);
        return ResponseEntity.ok(Map.of("threshold", threshold));
    }
}
