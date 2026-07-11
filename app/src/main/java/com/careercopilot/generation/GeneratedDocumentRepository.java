package com.careercopilot.generation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocumentEntity, UUID> {
    List<GeneratedDocumentEntity> findByApplicationId(UUID applicationId);
}
