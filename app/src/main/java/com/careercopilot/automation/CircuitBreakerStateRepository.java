package com.careercopilot.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CircuitBreakerStateRepository extends JpaRepository<CircuitBreakerStateEntity, UUID> {
    Optional<CircuitBreakerStateEntity> findByScope(String scope);
}
