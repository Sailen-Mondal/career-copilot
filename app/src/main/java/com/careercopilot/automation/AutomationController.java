package com.careercopilot.automation;

import com.careercopilot.profile.MasterProfile;
import com.careercopilot.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for automation management: kill switch, status, and event log.
 *
 * <p>All routes require a valid {@code X-API-Key} header.
 */
@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private static final Logger log = LoggerFactory.getLogger(AutomationController.class);

    private final KillSwitchService killSwitchService;
    private final StringRedisTemplate redisTemplate;
    private final ProfileService profileService;
    private final String resultsStream;

    public AutomationController(
            KillSwitchService killSwitchService,
            StringRedisTemplate redisTemplate,
            ProfileService profileService,
            @Value("${automation.streams.results-stream:cc:automation:results}") String resultsStream) {
        this.killSwitchService = killSwitchService;
        this.redisTemplate = redisTemplate;
        this.profileService = profileService;
        this.resultsStream = resultsStream;
    }

    /**
     * Activates or deactivates the global kill switch.
     *
     * @param request body with {@code action}: "halt" or "resume"
     */
    @PostMapping("/kill-switch")
    public ResponseEntity<Map<String, Object>> killSwitch(@RequestBody KillSwitchRequest request) {
        if ("halt".equalsIgnoreCase(request.action())) {
            killSwitchService.halt();
        } else if ("resume".equalsIgnoreCase(request.action())) {
            killSwitchService.resume();
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid action. Use 'halt' or 'resume'."));
        }
        return ResponseEntity.ok(Map.of("halted", killSwitchService.isHalted()));
    }

    /** Activates the global kill switch (Pause). */
    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pause() {
        killSwitchService.halt();
        return ResponseEntity.ok(Map.of("halted", true));
    }

    /** Deactivates the global kill switch (Resume). */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resume() {
        killSwitchService.resume();
        return ResponseEntity.ok(Map.of("halted", false));
    }

    /** Updates shadow mode setting. */
    @PatchMapping("/shadow-mode")
    public ResponseEntity<Map<String, Object>> shadowMode(@RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            enabled = true;
        }
        redisTemplate.opsForValue().set("cc:automation:shadow-mode", String.valueOf(enabled));
        log.info("Shadow mode updated to: {}", enabled);
        return ResponseEntity.ok(Map.of("shadowMode", enabled));
    }

    /** Returns the current automation status. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String shadowModeVal = redisTemplate.opsForValue().get("cc:automation:shadow-mode");
        boolean shadowMode = !"false".equalsIgnoreCase(shadowModeVal);

        int threshold = profileService.getProfileByUserId("default-user")
                .map(MasterProfile::autonomyThreshold)
                .orElse(85);

        Map<String, Object> status = new HashMap<>();
        status.put("halted", killSwitchService.isHalted());
        status.put("shadowMode", shadowMode);
        status.put("threshold", threshold);
        return ResponseEntity.ok(status);
    }

    /**
     * Returns the last 20 events from the automation results stream.
     * Each event is a map of the stream message fields.
     */
    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> events() {
        List<Map<String, Object>> events = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().reverseRange(resultsStream,
                            org.springframework.data.domain.Range.unbounded(),
                            org.springframework.data.redis.connection.Limit.limit().count(20));

            if (records != null) {
                for (MapRecord<String, Object, Object> record : records) {
                    Map<String, Object> event = new HashMap<>();
                    record.getValue().forEach((k, v) -> event.put(String.valueOf(k), v));
                    event.put("messageId", record.getId().getValue());
                    events.add(event);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch automation events (Redis may be unavailable): {}",
                    e.getMessage());
        }
        return ResponseEntity.ok(events);
    }

    /** Request body for the kill-switch endpoint. */
    public record KillSwitchRequest(String action) {}
}
