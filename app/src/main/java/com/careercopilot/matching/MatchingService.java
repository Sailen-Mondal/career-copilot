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
