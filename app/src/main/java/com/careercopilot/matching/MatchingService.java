package com.careercopilot.matching;

import com.careercopilot.discovery.EmbeddingClient;
import com.careercopilot.discovery.JobEntity;
import com.careercopilot.discovery.JobRepository;
import com.careercopilot.discovery.JobStatus;
import com.careercopilot.profile.MasterProfileEntity;
import com.careercopilot.profile.MasterProfileRepository;
import com.careercopilot.profile.ProfileFactEntity;
import com.careercopilot.profile.ProfileFactRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public MatchingService(EmbeddingClient embeddingClient,
                           ProfileFactRepository profileFactRepository,
                           MasterProfileRepository masterProfileRepository,
                           JobRepository jobRepository) {
        this.embeddingClient = embeddingClient;
        this.profileFactRepository = profileFactRepository;
        this.masterProfileRepository = masterProfileRepository;
        this.jobRepository = jobRepository;
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
        if (job.getWorkAuthRequired() != null &&
                !job.getWorkAuthRequired().equalsIgnoreCase(
                        profile.getWorkAuthorization() != null
                                ? profile.getWorkAuthorization().name()
                                : "")) {
            return ineligible("Work authorization requirement not met: "
                    + job.getWorkAuthRequired());
        }

        Set<String> blocklist = profile.getBlocklistCompanies();
        if (blocklist != null) {
            String jobCompany = job.getCompany();
            boolean blocked = blocklist.stream()
                    .anyMatch(b -> b.equalsIgnoreCase(jobCompany));
            if (blocked) {
                return ineligible("Company is on the blocklist: " + job.getCompany());
            }
        }

        if (job.getStatus() != JobStatus.ACTIVE) {
            return ineligible("Job is not active (status=" + job.getStatus() + ")");
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

        return new MatchResult(
                total,
                true,
                null,
                Map.of("embedding", embeddingScore, "keyword", keywordScore)
        );
    }

    // ---- Private helpers ----

    private static MatchResult ineligible(String reason) {
        return new MatchResult(0, false, reason, Map.of());
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
