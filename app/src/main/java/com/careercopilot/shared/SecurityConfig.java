package com.careercopilot.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration — API-key auth, stateless, no sessions.
 *
 * <p>Protected routes require the {@code X-API-Key} header (see {@link ApiKeyAuthFilter}).
 * The {@code /actuator/health} endpoint is publicly accessible for load-balancer
 * health checks without authentication.
 *
 * <p>TODO M-later: Replace with JWT / OAuth2 when multi-user support is needed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.api-key}")
    private String apiKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — stateless API, no browser form sessions
            .csrf(AbstractHttpConfigurer::disable)

            // Enable CORS
            .cors(cors -> cors.configurationSource(request -> {
                var config = new org.springframework.web.cors.CorsConfiguration();
                config.setAllowedOrigins(java.util.List.of("*"));
                config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(java.util.List.of("*"));
                config.setAllowCredentials(false);
                return config;
            }))

            // No sessions — each request is independently authenticated
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Route authorization
            .authorizeHttpRequests(auth -> auth
                // Health check — public (load balancers, Docker healthchecks)
                .requestMatchers("/actuator/health").permitAll()
                // Everything else requires a valid API key
                .anyRequest().hasRole("API"))

            // Inject API-key filter before Spring's default auth filter
            .addFilterBefore(
                new ApiKeyAuthFilter(apiKey),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
