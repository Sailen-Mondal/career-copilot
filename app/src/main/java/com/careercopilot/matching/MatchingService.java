package com.careercopilot.matching;
import com.careercopilot.discovery.EmbeddingClient;
import com.careercopilot.discovery.JobEntity;
import com.careercopilot.discovery.JobRepository;
import com.careercopilot.discovery.JobStatus;
import com.careercopilot.profile.MasterProfileEntity;
import com.careercopilot.profile.MasterProfileRepository;
import com.careercopilot.profile.ProfileFactEntity;
import com.careercopilot.profile.ProfileFactRepository;
import com.careercopilot.profile.WorkAuthorization;
import com.careercopilot.shared.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scores a job against a candidate profile using embedding cosine similarity
 * and keyword intersection, preceded by hard eligibility pre-filters.
 */
@Service
public class MatchingService {

    private final EmbeddingClient embeddingClient;
    private final ProfileFactRepository profileFactRepository;
    private final MasterProfileRepository masterProfileRepository;
    private final JobRepository jobRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public MatchingService(EmbeddingClient embeddingClient,
                           ProfileFactRepository profileFactRepository,
                           MasterProfileRepository masterProfileRepository,
                           JobRepository jobRepository,
                           LlmClient llmClient,
                           ObjectMapper objectMapper) {
        this.embeddingClient = embeddingClient;
        this.profileFactRepository = profileFactRepository;
        this.masterProfileRepository = masterProfileRepository;
        this.jobRepository = jobRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Computes a 0–100 match score for the given job and profile.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load entities, throw 404 if missing.</li>
     *   <li>Run eligibility pre-filters (work auth, blocklist, job status).</li>
     *   <li>Embedding score 0–50 via cosine similarity.</li>
     *   <li>Keyword score 0–50 via skill intersection.</li>
     * </ol>
     *
     * @param jobId     the job to score
     * @param profileId the candidate profile
     * @return a {@link MatchResult} containing the score and breakdown
     */
    public MatchResult scoreJob(UUID jobId, UUID profileId) {
        // Load entities
        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Job not found: " + jobId));
        MasterProfileEntity profile = masterProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Profile not found: " + profileId));
        List<ProfileFactEntity> facts = profileFactRepository.findByMasterProfileId(profileId);

        // ---- ELIGIBILITY PRE-FILTERS ----
        // 1. Work authorization
        if (job.getWorkAuthRequired() != null) {
            String required = job.getWorkAuthRequired();
            WorkAuthorization candidateAuth = profile.getWorkAuthorization();
            if (required.equalsIgnoreCase("US_CITIZEN")) {
                if (candidateAuth != WorkAuthorization.US_CITIZEN) {
                    return ineligible("Work authorization requirement not met: " + required);
                }
            } else if (required.equalsIgnoreCase("GREEN_CARD")) {
                if (candidateAuth != WorkAuthorization.US_CITIZEN && candidateAuth != WorkAuthorization.GREEN_CARD) {
                    return ineligible("Work authorization requirement not met: " + required);
                }
            } else {
                if (!required.equalsIgnoreCase(candidateAuth != null ? candidateAuth.name() : "")) {
                    return ineligible("Work authorization requirement not met: " + required);
                }
            }
        }

        // 2. Visa sponsorship needed vs available
        if (profile.isVisaSponsorshipNeeded() && !job.isSponsorshipAvailable()) {
            return ineligible("Visa sponsorship needed but not available for this job.");
        }

        // 3. Blocklist
        Set<String> blocklist = profile.getBlocklistCompanies();
        if (blocklist != null) {
            String jobCompany = job.getCompany();
            boolean blocked = blocklist.stream()
                    .anyMatch(b -> b.equalsIgnoreCase(jobCompany));
            if (blocked) {
                return ineligible("Company is on the blocklist: " + job.getCompany());
            }
        }

        // 4. Job status
        if (job.getStatus() != JobStatus.ACTIVE) {
            return ineligible("Job is not active (status=" + job.getStatus() + ")");
        }

        // 5. Seniority Filter
        if (job.getSeniority() != null) {
            double totalYears = 0.0;
            for (ProfileFactEntity fact : facts) {
                if (fact.getType() == com.careercopilot.profile.FactType.EXPERIENCE && fact.getStartDate() != null) {
                    LocalDate start = fact.getStartDate();
                    LocalDate end = fact.getEndDate() != null ? fact.getEndDate() : LocalDate.now();
                    long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                    totalYears += days / 365.25;
                }
            }
            if (job.getSeniority().equalsIgnoreCase("SENIOR") && totalYears < 3.0) {
                return ineligible("Seniority filter: Job requires SENIOR experience, candidate has " + String.format("%.2f", totalYears) + " years.");
            }
            if (job.getSeniority().equalsIgnoreCase("JUNIOR") && totalYears > 8.0) {
                return ineligible("Seniority filter: Job requires JUNIOR experience, candidate has " + String.format("%.2f", totalYears) + " years (overqualified).");
            }
        }

        // 6. Location Filter
        String jobLocType = job.getLocationType() != null ? job.getLocationType().toLowerCase() : "";
        String prefRemote = profile.getRemotePreference() != null ? profile.getRemotePreference().toLowerCase() : "";

        if (jobLocType.equals("remote") && prefRemote.equals("onsite")) {
            return ineligible("Location filter: Job is remote, but candidate preference is onsite.");
        }
        if ((jobLocType.equals("onsite") || jobLocType.equals("hybrid")) && prefRemote.equals("remote")) {
            return ineligible("Location filter: Job is " + jobLocType + ", but candidate preference is remote.");
        }

        if ((jobLocType.equals("onsite") || jobLocType.equals("hybrid")) && profile.getLocations() != null && !profile.getLocations().isEmpty()) {
            boolean locationMatched = false;
            String jobLoc = job.getLocation() != null ? job.getLocation().toLowerCase() : "";
            for (String prefLoc : profile.getLocations()) {
                String normalizedPref = prefLoc.toLowerCase().trim();
                if (jobLoc.contains(normalizedPref) || normalizedPref.contains(jobLoc)) {
                    locationMatched = true;
                    break;
                }
            }
            if (!locationMatched) {
                return ineligible("Location filter: Job location '" + job.getLocation() + "' does not match preferred locations: " + profile.getLocations());
            }
        }

        // ---- EMBEDDING SCORE (0–50) ----
        String profileText = facts.stream()
                .map(ProfileFactEntity::getBulletText)
                .collect(Collectors.joining(" "));

        float[] profileEmbedding = embeddingClient.getEmbedding(profileText);

        float[] jobEmbedding = job.getEmbeddingVector();
        if (jobEmbedding == null) {
            String desc = job.getDescriptionClean() != null
                    ? job.getDescriptionClean()
                    : (job.getDescriptionRaw() != null ? job.getDescriptionRaw() : "");
            jobEmbedding = embeddingClient.getEmbedding(desc);
            job.setEmbeddingVector(jobEmbedding);
            jobRepository.save(job);
        }

        double cosine = cosineSimilarity(profileEmbedding, jobEmbedding);
        int embeddingScore = (int) Math.round(cosine * 50.0);
        embeddingScore = Math.max(0, Math.min(50, embeddingScore));

        // ---- KEYWORD SCORE (0–50) ----
        Set<String> profileSkills = facts.stream()
                .flatMap(f -> f.getSkills() == null ? Set.<String>of().stream() : f.getSkills().stream())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> jobRequiredSkills = job.getRequiredSkills() == null
                ? Set.of()
                : job.getRequiredSkills();

        long intersection = jobRequiredSkills.stream()
                .filter(s -> profileSkills.contains(s.toLowerCase()))
                .count();

        int keywordScore = (int) Math.min(50L,
                intersection * 50L / Math.max(1L, jobRequiredSkills.size()));

        int total = embeddingScore + keywordScore;
        int originalTotal = total;
        int llmAdjustment = 0;
        String reasoning = "Base score outside borderline range (60-85). LLM check bypassed.";

        if (total >= 60 && total <= 85) {
            try {
                String systemPrompt = """
                        You are an expert recruiter evaluating the match between a job description and a candidate's profile facts.
                        Analyze the semantic fit, check if the candidate's actual experience matches the role requirements, and determine a semantic adjustment score.
                        You must output exactly a JSON object matching this structure:
                        {
                          "scoreAdjustment": <integer between -15 and 15>,
                          "reasoning": "<brief explanation of the semantic match or discrepancies>"
                        }
                        Do not output any markdown code blocks, backticks, or extra text. Output raw JSON only.
                        """;

                StringBuilder userPromptBuilder = new StringBuilder();
                userPromptBuilder.append("CANDIDATE FACTS:\n");
                for (ProfileFactEntity f : facts) {
                    userPromptBuilder.append("- ").append(f.getBulletText()).append("\n");
                }

                userPromptBuilder.append("\nJOB DESCRIPTION:\n")
                        .append(job.getDescriptionClean() != null ? job.getDescriptionClean() : "")
                        .append("\n");

                String llmResponse = llmClient.generate(systemPrompt, userPromptBuilder.toString());
                
                if (llmResponse.contains("```json")) {
                    llmResponse = llmResponse.substring(llmResponse.indexOf("```json") + 7);
                    if (llmResponse.contains("```")) {
                        llmResponse = llmResponse.substring(0, llmResponse.indexOf("```"));
                    }
                } else if (llmResponse.contains("```")) {
                    llmResponse = llmResponse.substring(llmResponse.indexOf("```") + 3);
                    if (llmResponse.contains("```")) {
                        llmResponse = llmResponse.substring(0, llmResponse.indexOf("```"));
                    }
                }
                llmResponse = llmResponse.trim();

                com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(llmResponse);
                if (rootNode.has("scoreAdjustment")) {
                    llmAdjustment = rootNode.get("scoreAdjustment").asInt();
                    llmAdjustment = Math.max(-15, Math.min(15, llmAdjustment));
                }
                if (rootNode.has("reasoning")) {
                    reasoning = rootNode.get("reasoning").asText();
                } else {
                    reasoning = "LLM semantic evaluation completed.";
                }
                
                total = Math.max(0, Math.min(100, total + llmAdjustment));

            } catch (Exception e) {
                reasoning = "LLM semantic evaluation failed: " + e.getMessage() + ". Using base score.";
            }
        }

        Map<String, Integer> breakdown = Map.of(
                "embedding", embeddingScore,
                "keyword", keywordScore,
                "baseTotal", originalTotal,
                "llmAdjustment", llmAdjustment
        );

        return new MatchResult(
                total,
                true,
                null,
                breakdown,
                reasoning
        );
    }

    // ---- Private helpers ----

    private static MatchResult ineligible(String reason) {
        return new MatchResult(0, false, reason, Map.of(), "Ineligible: " + reason);
    }

    /**
     * Computes cosine similarity between two vectors.
     * Returns a value in [-1, 1]; returns 0.0 if either vector is zero-length.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length != a.length) {
            return 0.0;
        }
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            magA += (double) a[i] * a[i];
            magB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(magA) * Math.sqrt(magB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }
}
