package com.yellow.trade.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Registers token verification across the protected prefix.

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    static final int ORDER = CorsConfig.ORDER + 10;

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            JwtTokenVerifier verifier, ObjectMapper objectMapper, JwtProperties properties) {

        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthenticationFilter(verifier, objectMapper));
        registration.addUrlPatterns(properties.protectedPathPrefix() + "*");
        // After CorsConfig's filter, which has to answer a browser's preflight
        // before this filter refuses it for carrying no token.
        registration.setOrder(ORDER);
        registration.setName("jwtAuthenticationFilter");
        return registration;
    }
}
