package com.careercopilot.discovery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    Optional<JobEntity> findByDedupKey(String dedupKey);

    @Query(value = "SELECT * FROM job j WHERE j.embedding_vector IS NOT NULL AND (1.0 - (j.embedding_vector <=> CAST(:embedding AS vector))) > :threshold ORDER BY j.embedding_vector <=> CAST(:embedding AS vector) ASC LIMIT 1", nativeQuery = true)
    Optional<JobEntity> findNearestSemanticMatch(@Param("embedding") String embedding, @Param("threshold") double threshold);
}
