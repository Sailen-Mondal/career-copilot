package com.careercopilot.automation;

import java.time.Instant;

public record CircuitBreakerState(
        String scope,
        CircuitBreakerStatus status,
        Instant trippedAt,
        String reason,
        int errorCountWindow
) {
    public CircuitBreakerState {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("Circuit breaker scope is required.");
        }
        status = status == null ? CircuitBreakerStatus.CLOSED : status;
    }

    public boolean isOpen() {
        return status == CircuitBreakerStatus.OPEN;
    }
}
