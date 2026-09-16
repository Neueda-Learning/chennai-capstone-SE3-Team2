package com.yellow.trade.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.trade.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER = "Bearer ";

    private static final ErrorResponse UNAUTHORIZED =
            new ErrorResponse("AUTH-401", "Unauthorised");

    private final JwtTokenVerifier verifier;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenVerifier verifier, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token;
        try {
            token = bearerTokenFrom(request);
        } catch (TokenVerificationException e) {
            reject(request, response, e);
            return;
        }

        long accountId;
        try {
            accountId = verifier.verifyAndExtractAccountId(token);
        } catch (TokenVerificationException e) {
            reject(request, response, e);
            return;
        }

        request.setAttribute(CallerAccount.REQUEST_ATTRIBUTE, accountId);
        try {
            chain.doFilter(request, response);
        } finally {
            // The container may recycle the request object. Clearing it stops
            // a later request inheriting an identity it never presented.
            request.removeAttribute(CallerAccount.REQUEST_ATTRIBUTE);
        }
    }

    private String bearerTokenFrom(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.MISSING,
                    header == null ? "no Authorization header" : "Authorization header was not a bearer token");
        }
        return header.substring(BEARER.length()).trim();
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        TokenVerificationException failure) throws IOException {
        // Everything an investigation needs goes here, on the server.
        log.warn("AUTH-401 on {} {}: {} ({})",
                request.getMethod(), request.getRequestURI(), failure.reason(), failure.getMessage());

        // The filter runs before the @ControllerAdvice, so the envelope is
        // written here by hand. It is the same shape every other failure uses:
        // the Angular application has one error handler because there is one
        // envelope, and an exception to it here would be a second.
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), UNAUTHORIZED);
    }
}
