package com.careercopilot.automation;

import com.careercopilot.applications.ApplicationService;
import com.careercopilot.applications.ApplicationStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@link AutomationResult} payloads from the Redis results stream
 * and updates application status and audit trail accordingly.
 *
 * <p>Runs a background polling thread that starts after the application
 * context is fully initialized. Respects the kill switch.
 */
@Service
public class AutomationResultConsumer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AutomationResultConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationService applicationService;
    private final KillSwitchService killSwitchService;

    private final String resultsStream;
    private final String consumerGroup;
    private final String consumerName;

    private volatile boolean running = true;
    private Thread consumerThread;

    public AutomationResultConsumer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ApplicationService applicationService,
            KillSwitchService killSwitchService,
            @Value("${automation.streams.results-stream:cc:automation:results}") String resultsStream,
            @Value("${automation.streams.consumer-group:spring-consumers}") String consumerGroup,
            @Value("${automation.streams.consumer-name:app-1}") String consumerName) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.applicationService = applicationService;
        this.killSwitchService = killSwitchService;
        this.resultsStream = resultsStream;
        this.consumerGroup = consumerGroup;
        this.consumerName = consumerName;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        createConsumerGroupIfNeeded();
        consumerThread = new Thread(this::pollLoop, "automation-result-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("AutomationResultConsumer started — stream={}, group={}, consumer={}",
                resultsStream, consumerGroup, consumerName);
    }

    /** Stops the polling loop (called during shutdown). */
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    private void createConsumerGroupIfNeeded() {
        try {
            redisTemplate.opsForStream().createGroup(resultsStream, ReadOffset.from("0"), consumerGroup);
            log.info("Created consumer group '{}' on stream '{}'", consumerGroup, resultsStream);
        } catch (Exception e) {
            // BUSYGROUP: consumer group already exists — safe to ignore
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer group '{}' already exists on stream '{}'",
                        consumerGroup, resultsStream);
            } else {
                log.warn("Could not create consumer group (Redis may not be available yet): {}",
                        e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void pollLoop() {
        while (running) {
            try {
                // Block up to 5 seconds waiting for messages
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                        Consumer.from(consumerGroup, consumerName),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(5)),
                        StreamOffset.create(resultsStream, ReadOffset.lastConsumed())
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> message : messages) {
                    processMessage(message);
                    // ACK the message
                    redisTemplate.opsForStream().acknowledge(resultsStream, consumerGroup, message.getId());
                }
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.error("Error in automation result consumer: {}", e.getMessage());
                try {
                    Thread.sleep(2000); // Back off on errors
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("AutomationResultConsumer stopped");
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        try {
            Map<String, String> fields = objectMapper.convertValue(
                    message.getValue(), new TypeReference<Map<String, String>>() {});

            String applicationIdStr = fields.get("applicationId");
            String status = fields.get("status");
            String screenshotPath = fields.getOrDefault("screenshotPath", "");

            if (applicationIdStr == null || status == null) {
                log.warn("Invalid result message — missing applicationId or status: {}",
                        message.getId());
                return;
            }

            UUID applicationId = UUID.fromString(applicationIdStr);

            // Map worker status to ApplicationStatus
            ApplicationStatus appStatus = switch (status) {
                case "shadow_completed" -> ApplicationStatus.READY;
                case "submitted" -> ApplicationStatus.SUBMITTED;
                case "failed" -> ApplicationStatus.FAILED;
                default -> ApplicationStatus.BLOCKED;
            };

            applicationService.transitionStatus(applicationId, appStatus);
            applicationService.appendAuditEntry(applicationId,
                    "Playwright worker completed: status=" + status
                            + (screenshotPath.isEmpty() ? "" : ", screenshot=" + screenshotPath));

            log.info("Processed automation result: applicationId={}, status={}",
                    applicationIdStr, status);
        } catch (Exception e) {
            log.error("Failed to process automation result message {}: {}",
                    message.getId(), e.getMessage());
        }
    }
}
