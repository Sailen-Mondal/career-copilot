package com.careercopilot.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnswerCacheRepository extends JpaRepository<AnswerCacheEntity, UUID> {

    @Query(value = """
        SELECT * FROM answer_cache 
        WHERE (scope IS NULL OR scope = :scope) 
          AND (question_embedding <=> CAST(:embedding AS vector)) <= 0.15 
        ORDER BY question_embedding <=> CAST(:embedding AS vector) 
        LIMIT 1
        """, nativeQuery = true)
    Optional<AnswerCacheEntity> findSemanticMatch(
            @Param("embedding") float[] embedding, 
            @Param("scope") String scope);

    @Query(value = """
        SELECT * FROM answer_cache 
        WHERE question_text = :questionText 
          AND (scope IS NULL OR scope = :scope)
        LIMIT 1
        """, nativeQuery = true)
    Optional<AnswerCacheEntity> findExactMatch(
            @Param("questionText") String questionText, 
            @Param("scope") String scope);
}
