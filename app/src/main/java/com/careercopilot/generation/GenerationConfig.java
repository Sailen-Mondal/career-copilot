package com.careercopilot.generation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the generation module.
 *
 * <p>Registers {@link GroundednessVerifier} as a singleton bean so it can be
 * injected into {@link LlmGenerationService}.
 */
@Configuration
public class GenerationConfig {

    @Bean
    public GroundednessVerifier groundednessVerifier() {
        return new GroundednessVerifier();
    }
}
