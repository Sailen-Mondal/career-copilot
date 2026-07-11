package com.careercopilot.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MasterProfileRepository extends JpaRepository<MasterProfileEntity, UUID> {
    Optional<MasterProfileEntity> findByUserId(String userId);
}
