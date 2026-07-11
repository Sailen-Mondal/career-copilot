package com.careercopilot.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that proves:
 * <ol>
 *   <li>Flyway migrations run successfully against a real pgvector Postgres instance.</li>
 *   <li>All expected tables exist after migration.</li>
 *   <li>The Spring context loads cleanly with the test datasource.</li>
 * </ol>
 *
 * <p>Uses Testcontainers to spin up a throwaway {@code pgvector/pgvector:pg17}
 * Docker container — the exact same image as {@code docker-compose.yml}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Schema integration test (Flyway + pgvector Postgres)")
class SchemaIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                    .withDatabaseName("career_copilot_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Use a safe API key so SecurityConfig doesn't blow up in context load
        registry.add("app.security.api-key",       () -> "test-key");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final List<String> EXPECTED_TABLES = List.of(
            "master_profile",
            "profile_fact",
            "job",
            "generated_document",
            "application",
            "automation_event",
            "circuit_breaker_state"
    );

    @Test
    @DisplayName("all V1 migration tables exist after Flyway runs")
    void allExpectedTablesExist() {
        for (String table : EXPECTED_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class,
                    table);
            assertThat(count)
                    .as("Expected table '%s' to exist after Flyway migration", table)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("pgvector extension is installed and job.embedding_vector and location columns exist")
    void pgvectorExtensionAndColumnsExist() {
        Integer extCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class);
        assertThat(extCount).as("pgvector extension should be installed").isEqualTo(1);

        Integer colCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'job' AND column_name = 'embedding_vector'",
                Integer.class);
        assertThat(colCount).as("job.embedding_vector column should exist").isEqualTo(1);

        Integer locCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'job' AND column_name = 'location'",
                Integer.class);
        assertThat(locCount).as("job.location column should exist").isEqualTo(1);
    }

    @Test
    @DisplayName("Flyway schema_history table has exactly four applied migrations")
    void flywayAppliedExactlyFourMigrations() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(migrationCount).as("Expected exactly four successful Flyway migrations").isEqualTo(4);
    }
}
