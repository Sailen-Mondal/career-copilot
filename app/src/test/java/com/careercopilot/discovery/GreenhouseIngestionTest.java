package com.careercopilot.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.discovery.greenhouse.use-mock=true")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Greenhouse Ingestion & Controller Test")
class GreenhouseIngestionTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                    .withDatabaseName("career_copilot_ingest_test")
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
    private GreenhouseSyncController greenhouseSyncController;

    @Autowired
    private JobRepository jobRepository;

    @Test
    @DisplayName("sync triggers ingestion of mock Greenhouse feed, then skips duplicates")
    void testIngestionAndDeduplication() {
        // Clear database before starting to ensure clean state
        jobRepository.deleteAll();

        // 1. Initial listing should be empty
        ResponseEntity<List<Job>> initialListResponse = greenhouseSyncController.getJobs();
        assertThat(initialListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initialListResponse.getBody()).isEmpty();

        // 2. Trigger sync
        ResponseEntity<List<Job>> syncResponse = greenhouseSyncController.syncJobs("google");
        assertThat(syncResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Job> syncedJobs = syncResponse.getBody();
        assertThat(syncedJobs).hasSize(2);
        assertThat(syncedJobs.get(0).company()).isEqualTo("google");
        assertThat(syncedJobs.get(0).title()).isEqualTo("Software Engineer II, Backend");
        assertThat(syncedJobs.get(0).location()).isEqualTo("San Francisco, CA");
        assertThat(syncedJobs.get(1).title()).isEqualTo("Frontend Developer");
        assertThat(syncedJobs.get(1).location()).isEqualTo("Remote");

        // Verify count in DB is 2
        assertThat(jobRepository.count()).isEqualTo(2);

        // 3. Trigger sync again. Since these jobs are duplicates, they should be skipped.
        ResponseEntity<List<Job>> syncResponse2 = greenhouseSyncController.syncJobs("google");
        assertThat(syncResponse2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(syncResponse2.getBody()).isEmpty(); // 0 new jobs synced

        // Verify count in DB is still 2
        assertThat(jobRepository.count()).isEqualTo(2);

        // 4. List all jobs via controller
        ResponseEntity<List<Job>> getJobsResponse = greenhouseSyncController.getJobs();
        assertThat(getJobsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getJobsResponse.getBody()).hasSize(2);
    }
}
