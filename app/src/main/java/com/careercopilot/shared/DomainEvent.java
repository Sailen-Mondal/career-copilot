package com.careercopilot.shared;

import java.time.Instant;

public interface DomainEvent {
    String eventType();

    Instant occurredAt();
}
