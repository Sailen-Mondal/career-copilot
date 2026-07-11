package com.careercopilot.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Job Deduplication and Semantic Search Integration Test")
class JobDeduplicationIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                    .withDatabaseName("career_copilot_job_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.api-key", () -> "test-key");
    }

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Test
    @DisplayName("can persist and query job with vector embedding and find nearest semantic matches")
    void testSemanticSimilarityQuery() {
        String title = "Software Engineer II, Backend";
        String description = "We are looking for a Software Engineer II with experience in Spring Boot and PostgreSQL.";
        float[] embedding1 = embeddingClient.getEmbedding(description);

        Job job1 = new Job(
                UUID.randomUUID(),
                "greenhouse",
                "ext-1",
                URI.create("https://example.com/job1"),
                "Google",
                title,
                "San Francisco, CA",
                "onsite",
                description,
                description,
                Set.of("Java"),
                null,
                null,
                null,
                false,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                JobStatus.ACTIVE,
                "google|software engineer|sanfranciscoca",
                embedding1
        );

        jobRepository.save(new JobEntity(job1));

        // 1. Exact match query
        String embedding1Str = JobEntity.serializeVectorToString(embedding1);
        Optional<JobEntity> match1 = jobRepository.findNearestSemanticMatch(embedding1Str, 0.98);
        assertThat(match1).isPresent();
        assertThat(match1.get().getTitle()).isEqualTo(title);

        // 2. Perturbed embedding query (similarity ~ 0.999+)
        float[] perturbedEmbedding = new float[1536];
        double sumSquare = 0;
        for (int i = 0; i < 1536; i++) {
            perturbedEmbedding[i] = embedding1[i] + 0.001f;
            sumSquare += perturbedEmbedding[i] * perturbedEmbedding[i];
        }
        double magnitude = Math.sqrt(sumSquare);
        for (int i = 0; i < 1536; i++) {
            perturbedEmbedding[i] = (float) (perturbedEmbedding[i] / magnitude);
        }

        String perturbedEmbeddingStr = JobEntity.serializeVectorToString(perturbedEmbedding);
        Optional<JobEntity> match2 = jobRepository.findNearestSemanticMatch(perturbedEmbeddingStr, 0.98);
        assertThat(match2).isPresent();

        // 3. Different embedding query (similarity will be low)
        String differentDesc = "We are seeking a graphic designer for marketing campaigns.";
        float[] embeddingDiff = embeddingClient.getEmbedding(differentDesc);
        String embeddingDiffStr = JobEntity.serializeVectorToString(embeddingDiff);
        Optional<JobEntity> matchDiff = jobRepository.findNearestSemanticMatch(embeddingDiffStr, 0.98);
        assertThat(matchDiff).isEmpty();
    }
}
