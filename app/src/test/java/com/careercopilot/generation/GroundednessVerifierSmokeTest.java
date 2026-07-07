package com.careercopilot.generation;

import com.careercopilot.profile.FactType;
import com.careercopilot.profile.ProfileFact;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GroundednessVerifierSmokeTest {
    public static void main(String[] args) {
        UUID factId = UUID.randomUUID();
        ProfileFact fact = new ProfileFact(
                factId,
                FactType.EXPERIENCE,
                "ExampleCo",
                "Backend Engineer",
                LocalDate.of(2022, 1, 1),
                null,
                "Built Java services backed by PostgreSQL.",
                Set.of("Java", "PostgreSQL")
        );

        GeneratedDocument grounded = new GeneratedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.RESUME,
                "Built Java services backed by PostgreSQL. [fact:" + factId + "]",
                Instant.now(),
                List.of(factId)
        );

        GroundednessVerifier verifier = new GroundednessVerifier();
        GroundednessReport pass = verifier.verify(grounded, List.of(fact));
        require(pass.passed(), "Expected document with known fact marker to pass.");

        GeneratedDocument ungrounded = new GeneratedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.COVER_LETTER,
                "Unverified: led a global AI migration.",
                Instant.now(),
                List.of(factId)
        );

        GroundednessReport fail = verifier.verify(ungrounded, List.of(fact));
        require(!fail.passed(), "Expected explicitly unverified text to fail.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
