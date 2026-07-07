package com.careercopilot.generation;

import com.careercopilot.profile.FactType;
import com.careercopilot.profile.ProfileFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GroundednessVerifier")
class GroundednessVerifierTest {

    private GroundednessVerifier verifier;
    private UUID factId;
    private ProfileFact fact;

    @BeforeEach
    void setUp() {
        verifier = new GroundednessVerifier();
        factId = UUID.randomUUID();
        fact = new ProfileFact(
                factId,
                FactType.EXPERIENCE,
                "ExampleCo",
                "Backend Engineer",
                LocalDate.of(2022, 1, 1),
                null,
                "Built Java services backed by PostgreSQL.",
                Set.of("Java", "PostgreSQL")
        );
    }

    @Test
    @DisplayName("passes when all fact markers are known and listed in sourceFactIds")
    void passes_whenAllFactMarkersAreGrounded() {
        GeneratedDocument doc = new GeneratedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.RESUME,
                "Built Java services backed by PostgreSQL. [fact:" + factId + "]",
                Instant.now(),
                List.of(factId)
        );

        GroundednessReport report = verifier.verify(doc, List.of(fact));

        assertThat(report.passed()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    @DisplayName("fails when document contains 'unverified:' text")
    void fails_whenDocumentContainsExplicitUnverifiedText() {
        GeneratedDocument doc = new GeneratedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.COVER_LETTER,
                "Unverified: led a global AI migration.",
                Instant.now(),
                List.of(factId)
        );

        GroundednessReport report = verifier.verify(doc, List.of(fact));

        assertThat(report.passed()).isFalse();
        assertThat(report.issues()).anyMatch(i -> i.contains("explicitly marked as unverified"));
    }

    @Test
    @DisplayName("fails when sourceFactIds references a fact not in the available set")
    void fails_whenSourceFactIdIsUnavailable() {
        UUID unknownFactId = UUID.randomUUID();
        GeneratedDocument doc = new GeneratedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.RESUME,
                "Some content without markers.",
                Instant.now(),
                List.of(unknownFactId)
        );

        GroundednessReport report = verifier.verify(doc, List.of(fact));

        assertThat(report.passed()).isFalse();
        assertThat(report.issues()).anyMatch(i -> i.contains("unavailable profile fact"));
    }

    @Test
    @DisplayName("fails when a fact marker in content is not listed in sourceFactIds")
    void fails_whenFactMarkerNotInSourceFactIds() {
        UUID extraFactId = UUID.randomUUID();
        GeneratedDocument doc = new GeneratedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.RESUME,
                "Built things. [fact:" + factId + "] and also [fact:" + extraFactId + "]",
                Instant.now(),
                List.of(factId) // extraFactId deliberately NOT listed here
        );

        GroundednessReport report = verifier.verify(doc, List.of(fact));

        assertThat(report.passed()).isFalse();
        assertThat(report.issues()).anyMatch(i -> i.contains("not listed in sourceFactIds"));
    }

    @Test
    @DisplayName("passes when document has no markers and no sourceFactIds")
    void passes_whenDocumentHasNoMarkersAndNoFacts() {
        GeneratedDocument doc = new GeneratedDocument(
                UUID.randomUUID(),
                UUID.randomUUID(),
                DocumentType.RESUME,
                "A clean document with no fact markers.",
                Instant.now(),
                List.of()
        );

        GroundednessReport report = verifier.verify(doc, List.of(fact));

        assertThat(report.passed()).isTrue();
    }
}
