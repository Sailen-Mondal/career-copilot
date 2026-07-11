package com.careercopilot.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProfileFactRepository extends JpaRepository<ProfileFactEntity, UUID> {
    List<ProfileFactEntity> findByMasterProfileId(UUID masterProfileId);
}
