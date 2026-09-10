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

/**
 * Answers "does this caller hold a valid token" once, for every route under
 * the protected prefix, before any controller runs.
 *
 * It does NOT answer "may this caller reach that account". That question needs
 * the account key, which this filter cannot know for every route -- it is on
 * the path for one endpoint, in the body for another. Answering it here would
 * mean parsing each route's shape in a filter, and a route added in Sprint 10
 * would be unprotected until somebody remembered to teach the filter about it.
 * So authorisation lives where the key is known and answers ACC-403.
 *
 * A missing header, a wrong scheme, an expired token and a forged signature
 * all leave here as the same AUTH-401 with the same body. A more specific
 * message tells an attacker which of the four they got, and therefore what to
 * try next. What an investigation needs is on the log line instead.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER = "Bearer ";

    /**
     * One message for every failure. Held as a constant so that nobody can
     * make one branch more helpful than the others by accident.
     */
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

    /**
     * A wrong scheme is treated exactly like no header at all. "Basic ..." on
     * a route that takes bearer tokens is not a more forgivable mistake than
     * sending nothing, and saying so would confirm which schemes exist.
     */
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
