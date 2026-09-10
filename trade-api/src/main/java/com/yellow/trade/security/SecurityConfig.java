package com.yellow.trade.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers token verification across the protected prefix.
 *
 * The URL pattern comes from configuration rather than being written here, so
 * that the answer to "which routes are protected" is one line a reviewer can
 * read, and so the Sprint 10 extension routes -- which land under the same
 * /api/v1/ prefix -- are protected the moment they exist rather than when
 * somebody remembers to add them.
 *
 * Ordered first: nothing else should get to look at an unauthenticated
 * request. The actuator health endpoint sits outside the prefix and stays
 * reachable, which is what lets the orchestrator health-check the container
 * without a token.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            JwtTokenVerifier verifier, ObjectMapper objectMapper, JwtProperties properties) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthenticationFilter(verifier, objectMapper));
        registration.addUrlPatterns(properties.protectedPathPrefix() + "*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("jwtAuthenticationFilter");
        return registration;
    }
}
