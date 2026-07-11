package com.careercopilot.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JobNormalizerTest {

    @Test
    @DisplayName("Company name is correctly normalized")
    void companyNormalization() {
        assertThat(JobNormalizer.generateDedupKey("Google LLC", "Software Engineer", "San Francisco"))
                .startsWith("google|");
        assertThat(JobNormalizer.generateDedupKey("Stripe, Inc.", "Software Engineer", "San Francisco"))
                .startsWith("stripe|");
        assertThat(JobNormalizer.generateDedupKey("Microsoft Corporation", "Software Engineer", "San Francisco"))
                .startsWith("microsoft|");
    }

    @Test
    @DisplayName("Title is correctly normalized by stripping seniority and brackets")
    void titleNormalization() {
        assertThat(JobNormalizer.generateDedupKey("Google", "Senior Software Engineer (Remote)", "Remote"))
                .contains("|software engineer|");
        assertThat(JobNormalizer.generateDedupKey("Google", "Lead Frontend Developer [Hybrid]", "Seattle"))
                .contains("|frontend developer|");
        assertThat(JobNormalizer.generateDedupKey("Google", "Staff Systems Engineer II", "New York"))
                .contains("|systems engineer|");
    }

    @Test
    @DisplayName("Location is correctly normalized (remote indicators map to 'remote')")
    void locationNormalization() {
        assertThat(JobNormalizer.generateDedupKey("Google", "Engineer", "San Francisco, CA"))
                .endsWith("|sanfranciscoca");
        assertThat(JobNormalizer.generateDedupKey("Google", "Engineer", "US Remote"))
                .endsWith("|remote");
        assertThat(JobNormalizer.generateDedupKey("Google", "Engineer", "Virtual, Anywhere"))
                .endsWith("|remote");
    }
}
