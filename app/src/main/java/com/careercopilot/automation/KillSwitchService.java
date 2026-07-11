package com.careercopilot.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global kill switch for the automation engine.
 *
 * <p>When halted, the backend stops publishing new commands and the result
 * consumer stops processing incoming results. A HALT sentinel is published
 * to the jobs stream so the TypeScript worker can also stop.
 */
@Service
public class KillSwitchService {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchService.class);

    private final AtomicBoolean halted = new AtomicBoolean(false);
    private final StringRedisTemplate redisTemplate;
    private final String jobsStream;

    public KillSwitchService(
            StringRedisTemplate redisTemplate,
            @Value("${automation.streams.jobs-stream:cc:automation:jobs}") String jobsStream) {
        this.redisTemplate = redisTemplate;
        this.jobsStream = jobsStream;
    }

    /** Halts all automation and publishes a HALT sentinel to the jobs stream. */
    public void halt() {
        halted.set(true);
        try {
            StringRecord record = StringRecord.of(Map.of("HALT", "true"))
                    .withStreamKey(jobsStream);
            redisTemplate.opsForStream().add(record);
            log.warn("Kill switch ACTIVATED — HALT sentinel published to {}", jobsStream);
        } catch (Exception e) {
            log.error("Failed to publish HALT sentinel (Redis may be unavailable): {}",
                    e.getMessage());
        }
    }

    /** Resumes automation processing. */
    public void resume() {
        halted.set(false);
        log.info("Kill switch DEACTIVATED — automation resumed");
    }

    /** Returns true if automation is currently halted. */
    public boolean isHalted() {
        return halted.get();
    }
}
