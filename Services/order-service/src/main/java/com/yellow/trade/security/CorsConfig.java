package com.yellow.trade.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Duration;
import java.util.List;

/**
 * Cross-origin rules for the trading UI.
 *
 * Runs before the token filter, and has to: a browser's preflight is an
 * OPTIONS request that carries no Authorization header, so the token filter
 * would refuse it with 401 and the browser would never send the real call.
 * Spring's CorsFilter answers the preflight itself and stops there.
 *
 * The allowed origins are an exact list from CORS_ALLOWED_ORIGINS. Any other
 * origin's preflight is refused, and so is any request a browser sends from
 * one. Requests with no Origin header -- curl, the executor -- are not
 * cross-origin and pass through untouched.
 */
@Configuration
public class CorsConfig {

    /** Ahead of the token filter in SecurityConfig. */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(
            @Value("${security.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // A bearer token in a header, never a cookie: nothing to send with credentials.
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofMinutes(10));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(ORDER);
        registration.setName("corsFilter");
        return registration;
    }
}
