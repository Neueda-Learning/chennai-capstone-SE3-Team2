package com.yellow.trade.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The filter, without a container.
 *
 * The point of these tests is the one the brief makes: four different failures
 * must be indistinguishable from outside. Asserting the status alone would not
 * catch a message that quietly says which one it was.
 */
class JwtAuthenticationFilterTest {

    private static final String ISSUER = "auth-service";
    private static final String SECRET = "a-test-signing-secret-of-at-least-32-bytes";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new JwtTokenVerifier(new JwtProperties(SECRET, "HS256", ISSUER, "/api/v1/")),
            new ObjectMapper());

    private static String signed(long accountId, Instant expiry, SecretKey key) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .claims(Map.of("accountId", accountId, "roles", List.of("CUSTOMER")))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    private MockHttpServletResponse runWith(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/3");
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    @Test
    @DisplayName("a valid token passes the request down the chain with the account attached")
    void validTokenPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/3");
        request.addHeader("Authorization", "Bearer " + signed(3L, Instant.now().plusSeconds(600), KEY));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus(), is(200));
    }

    @Test
    @DisplayName("all four failures answer 401 with one byte-identical body")
    void everyFailureAnswersIdentically() throws Exception {
        SecretKey attackersKey = Keys.hmacShaKeyFor(
                "an-entirely-different-secret-32-bytes-long".getBytes(StandardCharsets.UTF_8));

        List<MockHttpServletResponse> responses = List.of(
                runWith(null),                                                               // missing
                runWith("Bearer not.a.jwt"),                                                 // malformed
                runWith("Bearer " + signed(3L, Instant.now().minusSeconds(60), KEY)),        // expired
                runWith("Bearer " + signed(3L, Instant.now().plusSeconds(600), attackersKey))); // forged

        String first = responses.get(0).getContentAsString();
        for (MockHttpServletResponse response : responses) {
            assertThat(response.getStatus(), is(401));
            // Byte-identical, not merely "also a 401 with some message".
            assertThat(response.getContentAsString(), is(first));
        }
        assertThat(first, containsString("AUTH-401"));
    }

    @Test
    @DisplayName("the body never says which of the four failures occurred")
    void bodyLeaksNothing() throws Exception {
        String body = runWith("Bearer " + signed(3L, Instant.now().minusSeconds(60), KEY))
                .getContentAsString();

        assertThat(body, not(containsString("expired")));
        assertThat(body, not(containsString("Expired")));
        assertThat(body, not(containsString("signature")));
        assertThat(body, not(containsString("Exception")));
    }

    @Test
    @DisplayName("a wrong scheme is refused without reaching the verifier")
    void wrongSchemeIsRefused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/3");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus(), is(401));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("the verified identity is cleared once the request is done")
    void identityDoesNotOutliveTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/3");
        request.addHeader("Authorization", "Bearer " + signed(3L, Instant.now().plusSeconds(600), KEY));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        // A recycled request object must not carry an identity it never presented.
        assertThat(request.getAttribute(CallerAccount.REQUEST_ATTRIBUTE), is((Object) null));
    }
}
