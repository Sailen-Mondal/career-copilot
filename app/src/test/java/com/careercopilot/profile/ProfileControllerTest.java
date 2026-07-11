package com.careercopilot.profile;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ProfileController Integration Test")
class ProfileControllerTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                    .withDatabaseName("career_copilot_profile_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.api-key", () -> "secure-test-key");
    }

    @Autowired
    private ProfileController profileController;

    @Autowired
    private MasterProfileRepository masterProfileRepository;

    @Autowired
    private ProfileFactRepository profileFactRepository;

    @Test
    @DisplayName("Profile CRUD operations function correctly")
    void profileCrud() {
        // Clear db
        profileFactRepository.deleteAll();
        masterProfileRepository.deleteAll();

        // 1. Get profile when not exists -> 404
        ResponseEntity<MasterProfile> getResponse = profileController.getProfile();
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 2. Create Profile
        CreateProfileRequest request = new CreateProfileRequest(
                WorkAuthorization.US_CITIZEN,
                false,
                150000,
                Set.of("Remote", "San Francisco, CA"),
                "remote",
                Set.of("Blocked Inc"),
                10,
                75
        );
        ResponseEntity<MasterProfile> createResponse = profileController.createOrUpdateProfile(request);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        MasterProfile profile = createResponse.getBody();
        assertThat(profile).isNotNull();
        assertThat(profile.userId()).isEqualTo("default-user");
        assertThat(profile.workAuthorization()).isEqualTo(WorkAuthorization.US_CITIZEN);
        assertThat(profile.salaryFloor()).isEqualTo(150000);

        // 3. Get profile -> 200
        ResponseEntity<MasterProfile> getResponse2 = profileController.getProfile();
        assertThat(getResponse2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse2.getBody().workAuthorization()).isEqualTo(WorkAuthorization.US_CITIZEN);

        // 4. Add Fact
        CreateFactRequest factRequest = new CreateFactRequest(
                FactType.EXPERIENCE,
                "Acme Corp",
                "Senior Engineer",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2023, 12, 31),
                "Built outstanding scalable microservices.",
                Set.of("Java", "Spring Boot")
        );
        ResponseEntity<ProfileFact> addFactResponse = profileController.addFact(factRequest);
        assertThat(addFactResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProfileFact fact = addFactResponse.getBody();
        assertThat(fact).isNotNull();
        assertThat(fact.employer()).isEqualTo("Acme Corp");
        assertThat(fact.title()).isEqualTo("Senior Engineer");

        // 5. Update Fact
        CreateFactRequest factUpdate = new CreateFactRequest(
                FactType.EXPERIENCE,
                "Acme Corp Updated",
                "Lead Engineer",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2023, 12, 31),
                "Built outstanding scalable microservices.",
                Set.of("Java", "Spring Boot", "AWS")
        );
        ResponseEntity<ProfileFact> updateFactResponse = profileController.updateFact(fact.id(), factUpdate);
        assertThat(updateFactResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateFactResponse.getBody().employer()).isEqualTo("Acme Corp Updated");
        assertThat(updateFactResponse.getBody().title()).isEqualTo("Lead Engineer");

        // 6. Get Facts
        ResponseEntity<List<ProfileFact>> getFactsResponse = profileController.getFacts();
        assertThat(getFactsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getFactsResponse.getBody()).hasSize(1);
        assertThat(getFactsResponse.getBody().get(0).employer()).isEqualTo("Acme Corp Updated");

        // 7. Delete Fact
        ResponseEntity<Void> deleteResponse = profileController.deleteFact(fact.id());
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 8. Get Facts after deletion should be empty
        ResponseEntity<List<ProfileFact>> getFactsResponse2 = profileController.getFacts();
        assertThat(getFactsResponse2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getFactsResponse2.getBody()).isEmpty();
    }
}
