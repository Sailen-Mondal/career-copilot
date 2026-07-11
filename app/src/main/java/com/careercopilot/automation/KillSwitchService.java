package com.careercopilot.automation;

import com.careercopilot.applications.ApplicationEntity;
import com.careercopilot.applications.ApplicationRepository;
import com.careercopilot.applications.ApplicationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global kill switch for the automation engine.
 *
 * <p>When halted, the backend stops publishing new commands, transitions queued/in-flight
 * applications to BLOCKED, and sets a Redis key so the TypeScript worker stops immediately.
 */
@Service
public class KillSwitchService {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchService.class);
    private static final String REDIS_HALT_KEY = "cc:automation:halted";

    private final AtomicBoolean halted = new AtomicBoolean(false);
    private final StringRedisTemplate redisTemplate;
    private final String jobsStream;
    private final ApplicationRepository applicationRepository;

    public KillSwitchService(
            StringRedisTemplate redisTemplate,
            ApplicationRepository applicationRepository,
            @Value("${automation.streams.jobs-stream:cc:automation:jobs}") String jobsStream) {
        this.redisTemplate = redisTemplate;
        this.applicationRepository = applicationRepository;
        this.jobsStream = jobsStream;
        
        // Initialize state from Redis if present on startup
        try {
            String val = redisTemplate.opsForValue().get(REDIS_HALT_KEY);
            if ("true".equalsIgnoreCase(val)) {
                halted.set(true);
            }
        } catch (Exception e) {
            log.warn("Could not check startup kill-switch state from Redis: {}", e.getMessage());
        }
    }

    /** Halts all automation, sets Redis halt key, and transitions in-flight applications. */
    @Transactional
    public void halt() {
        halted.set(true);
        try {
            redisTemplate.opsForValue().set(REDIS_HALT_KEY, "true");
            StringRecord record = StringRecord.of(Map.of("HALT", "true"))
                    .withStreamKey(jobsStream);
            redisTemplate.opsForStream().add(record);
            log.warn("Kill switch ACTIVATED — HALT sentinel published and Redis key set");

            // Transition queued/in-flight applications to BLOCKED
            List<String> activeStatuses = List.of(
                    ApplicationStatus.QUEUED.name(),
                    ApplicationStatus.GENERATING.name(),
                    ApplicationStatus.VERIFYING.name(),
                    ApplicationStatus.READY.name()
            );

            List<ApplicationEntity> applications = applicationRepository.findAll();
            for (ApplicationEntity app : applications) {
                if (activeStatuses.contains(app.getStatus())) {
                    app.setStatus(ApplicationStatus.BLOCKED.name());
                    List<String> trail = app.getAuditTrail();
                    if (trail == null) {
                        trail = new ArrayList<>();
                    }
                    trail.add("[" + Instant.now().toString() + "] Halted by global kill switch.");
                    app.setAuditTrail(trail);
                    applicationRepository.save(app);
                    log.info("Transitioned active application {} to BLOCKED due to kill switch", app.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute halt operations: {}", e.getMessage());
        }
    }

    /** Resumes automation processing. */
    public void resume() {
        halted.set(false);
        try {
            redisTemplate.opsForValue().set(REDIS_HALT_KEY, "false");
            log.info("Kill switch DEACTIVATED — automation resumed");
        } catch (Exception e) {
            log.error("Failed to reset Redis halt key: {}", e.getMessage());
        }
    }

    /** Returns true if automation is currently halted. */
    public boolean isHalted() {
        return halted.get();
    }
}
