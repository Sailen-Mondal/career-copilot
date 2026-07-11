package com.careercopilot.applications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ApplicationEntity}.
 */
@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    /** Returns all applications with the given status string. */
    List<ApplicationEntity> findByStatus(String status);

    /** Counts applications submitted after the given timestamp (used for daily cap tracking). */
    long countBySubmittedAtAfter(Instant since);
}
