package com.careercopilot.automation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes {@link AutomationCommand} payloads to the Redis jobs stream
 * ({@code cc:automation:jobs}) for consumption by the Playwright worker.
 */
@Service
public class AutomationPublisher {

    private static final Logger log = LoggerFactory.getLogger(AutomationPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String jobsStream;
    private final KillSwitchService killSwitchService;

    public AutomationPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            KillSwitchService killSwitchService,
            @Value("${automation.streams.jobs-stream:cc:automation:jobs}") String jobsStream) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.killSwitchService = killSwitchService;
        this.jobsStream = jobsStream;
    }

    /**
     * Publishes an automation command to the Redis jobs stream.
     *
     * @param command the form-filling command to publish
     * @throws IllegalStateException if the kill switch is active
     */
    public void publish(AutomationCommand command) {
        if (killSwitchService.isHalted()) {
            throw new IllegalStateException("Kill switch is active — cannot publish automation commands");
        }

        Map<String, String> rawFields = objectMapper.convertValue(command,
                new TypeReference<Map<String, String>>() {});
        Map<String, String> fields = new HashMap<>();
        rawFields.forEach((k, v) -> {
            if (v != null) {
                fields.put(k, v);
            }
        });

        StringRecord record = StringRecord.of(fields).withStreamKey(jobsStream);
        var recordId = redisTemplate.opsForStream().add(record);

        log.info("Published automation command to {} — recordId={}, applicationId={}",
                jobsStream, recordId, command.applicationId());
    }
}
