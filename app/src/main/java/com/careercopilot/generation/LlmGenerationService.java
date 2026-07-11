package com.careercopilot.generation;

import com.careercopilot.profile.ProfileFact;
import com.careercopilot.profile.ProfileFactEntity;
import com.careercopilot.profile.ProfileFactRepository;
import com.careercopilot.shared.LlmClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates a resume or cover letter document by prompting the LLM with the
 * candidate's verified profile facts, then verifying groundedness before returning.
 *
 * <p>The LLM is strictly instructed to cite every claim with a {@code [fact:<UUID>]} marker
 * so that {@link GroundednessVerifier} can detect any hallucinations.
 */
@Service
public class LlmGenerationService {

    private final LlmClient llmClient;
    private final ProfileFactRepository profileFactRepository;
    private final GroundednessVerifier groundednessVerifier;

    public LlmGenerationService(LlmClient llmClient,
                                ProfileFactRepository profileFactRepository,
                                GroundednessVerifier groundednessVerifier) {
        this.llmClient = llmClient;
        this.profileFactRepository = profileFactRepository;
        this.groundednessVerifier = groundednessVerifier;
    }

    /**
     * Generates a grounded {@link GeneratedDocument} for the given application.
     *
     * @param applicationId      the application this document belongs to
     * @param profileId          the candidate's master profile ID
     * @param jobId              the job being applied to (for reference/logging)
     * @param type               RESUME or COVER_LETTER
     * @param jobDescriptionClean cleaned plain-text job description
     * @return a verified {@link GeneratedDocument}
     * @throws GroundednessException if the generated content fails the groundedness check
     */
    public GeneratedDocument generate(UUID applicationId,
                                      UUID profileId,
                                      UUID jobId,
                                      DocumentType type,
                                      String jobDescriptionClean) {
        // 1. Load facts
        List<ProfileFactEntity> factEntities = profileFactRepository.findByMasterProfileId(profileId);
        List<ProfileFact> facts = factEntities.stream()
                .map(ProfileFactEntity::toDomain)
                .collect(Collectors.toList());
        List<UUID> factIds = facts.stream().map(ProfileFact::id).collect(Collectors.toList());

        // 2. Build system prompt
        String systemPrompt = """
                You are a professional resume and cover letter writer.
                You must use ONLY the facts provided below — do not invent any information.
                Every claim you make must end with its source marker in the format [fact:<UUID>].
                Do not include any text explicitly marked as unverified.
                """;

        // 3. Build user prompt: numbered fact list + job description
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("CANDIDATE FACTS:\n");
        for (int i = 0; i < facts.size(); i++) {
            ProfileFact f = facts.get(i);
            userPromptBuilder.append(i + 1).append(". ")
                    .append("[fact:").append(f.id()).append("] ")
                    .append(f.bulletText());
            if (f.employer() != null) {
                userPromptBuilder.append(" (at ").append(f.employer()).append(")");
            }
            userPromptBuilder.append("\n");
        }

        userPromptBuilder.append("\n--- JOB DESCRIPTION ---\n")
                .append(jobDescriptionClean != null ? jobDescriptionClean : "")
                .append("\n--- END JOB DESCRIPTION ---\n")
                .append("\nGenerate a tailored ").append(type.name().replace("_", " ").toLowerCase())
                .append(" for the above job. For every claim, append the corresponding [fact:<UUID>] marker.");

        // 4. Call LLM
        String content = llmClient.generate(systemPrompt, userPromptBuilder.toString());

        // 5. Build GeneratedDocument deriving sourceFactIds from content markers
        Set<UUID> markerFactIds = groundednessVerifier.extractFactMarkers(content);
        List<UUID> actualFactIds = facts.stream()
                .map(ProfileFact::id)
                .filter(markerFactIds::contains)
                .collect(Collectors.toList());

        GeneratedDocument document = new GeneratedDocument(
                UUID.randomUUID(),
                applicationId,
                type,
                content,
                Instant.now(),
                actualFactIds
        );

        // 6. Verify groundedness
        GroundednessReport report = groundednessVerifier.verify(document, facts);
        if (!report.passed()) {
            throw new GroundednessException(report.issues());
        }

        return document;
    }
}
