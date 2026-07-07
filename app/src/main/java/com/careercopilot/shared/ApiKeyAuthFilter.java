package com.careercopilot.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless API-key authentication filter.
 *
 * <p>Reads the {@code X-API-Key} request header and validates it against the
 * configured key. On success, sets a {@link UsernamePasswordAuthenticationToken}
 * in the {@link SecurityContextHolder} so downstream security expressions work.
 *
 * <p>No session is created — {@code SessionCreationPolicy.STATELESS} is set in
 * {@link SecurityConfig}.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";
    private static final String PRINCIPAL = "career-copilot-api";

    private final String expectedApiKey;

    public ApiKeyAuthFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String providedKey = request.getHeader(HEADER);

        if (expectedApiKey.equals(providedKey)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    PRINCIPAL,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
