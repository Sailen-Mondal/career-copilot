package com.careercopilot.automation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "circuit_breaker_state")
public class CircuitBreakerStateEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String scope;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CircuitBreakerStatus status;

    @Column(name = "tripped_at")
    private Instant trippedAt;

    private String reason;

    @Column(name = "error_count_window", nullable = false)
    private int errorCountWindow;

    public CircuitBreakerStateEntity() {
    }

    public CircuitBreakerStateEntity(CircuitBreakerState domain) {
        this.id = UUID.randomUUID();
        this.scope = domain.scope();
        this.status = domain.status();
        this.trippedAt = domain.trippedAt();
        this.reason = domain.reason();
        this.errorCountWindow = domain.errorCountWindow();
    }

    public CircuitBreakerState toDomain() {
        return new CircuitBreakerState(
                this.scope,
                this.status,
                this.trippedAt,
                this.reason,
                this.errorCountWindow
        );
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public CircuitBreakerStatus getStatus() { return status; }
    public void setStatus(CircuitBreakerStatus status) { this.status = status; }

    public Instant getTrippedAt() { return trippedAt; }
    public void setTrippedAt(Instant trippedAt) { this.trippedAt = trippedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getErrorCountWindow() { return errorCountWindow; }
    public void setErrorCountWindow(int errorCountWindow) { this.errorCountWindow = errorCountWindow; }
}
