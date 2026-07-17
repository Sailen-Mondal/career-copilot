package com.careercopilot.profile;

import com.careercopilot.automation.CircuitBreakerStateEntity;
import com.careercopilot.automation.CircuitBreakerStateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {

    private static final String DEFAULT_USER_ID = "default-user";

    private final ProfileService profileService;
    private final CircuitBreakerStateRepository circuitBreakerStateRepository;

    public PolicyController(
            ProfileService profileService,
            CircuitBreakerStateRepository circuitBreakerStateRepository) {
        this.profileService = profileService;
        this.circuitBreakerStateRepository = circuitBreakerStateRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPolicy() {
        MasterProfile profile = profileService.getProfileByUserId(DEFAULT_USER_ID)
                .orElseGet(() -> new MasterProfile(
                        UUID.randomUUID(),
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
                        null,
                        Set.of()
                ));

        Map<String, String> platformBreakers = circuitBreakerStateRepository.findAll().stream()
                .collect(Collectors.toMap(
                        CircuitBreakerStateEntity::getScope,
                        entity -> entity.getStatus().name(),
                        (v1, v2) -> v1
                ));

        // Add defaults if empty
        if (!platformBreakers.containsKey("greenhouse")) {
            platformBreakers.put("greenhouse", "CLOSED");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("autonomyThreshold", profile.autonomyThreshold());
        response.put("dailyApplicationCap", profile.dailyApplicationCap());
        response.put("blocklistCompanies", profile.blocklistCompanies());
        response.put("searchKeywords", profile.searchKeywords());
        response.put("platformBreakerStates", platformBreakers);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updatePolicy(@RequestBody Map<String, Object> request) {
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
                    Set.of(),
                    null,
                    Set.of(),
                    3,
                    85,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Set.of()
            );
        } else {
            old = profileOpt.get();
        }

        int threshold = request.containsKey("autonomyThreshold")
                ? ((Number) request.get("autonomyThreshold")).intValue()
                : old.autonomyThreshold();

        int cap = request.containsKey("dailyApplicationCap")
                ? ((Number) request.get("dailyApplicationCap")).intValue()
                : old.dailyApplicationCap();

        Set<String> blocklist = old.blocklistCompanies();
        if (request.containsKey("blocklistCompanies")) {
            Object rawList = request.get("blocklistCompanies");
            if (rawList instanceof Collection<?>) {
                blocklist = ((Collection<?>) rawList).stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet());
            }
        }

        Set<String> searchKeywords = old.searchKeywords();
        if (request.containsKey("searchKeywords")) {
            Object rawList = request.get("searchKeywords");
            if (rawList instanceof Collection<?>) {
                searchKeywords = ((Collection<?>) rawList).stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet());
            }
        }

        MasterProfile updated = new MasterProfile(
                old.id(),
                old.userId(),
                old.workAuthorization(),
                old.visaSponsorshipNeeded(),
                old.salaryFloor(),
                old.locations(),
                old.remotePreference(),
                blocklist,
                cap,
                threshold,
                old.name(),
                old.email(),
                old.phone(),
                old.linkedinUrl(),
                old.websiteUrl(),
                searchKeywords
        );

        profileService.saveProfile(DEFAULT_USER_ID, updated);

        return getPolicy();
    }

    @PostMapping("/breakers/reset")
    public ResponseEntity<Void> resetBreakers() {
        circuitBreakerStateRepository.deleteAll();
        return ResponseEntity.ok().build();
    }
}
