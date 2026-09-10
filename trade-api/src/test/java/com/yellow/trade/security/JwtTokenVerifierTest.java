package com.yellow.trade.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The token fixture and the verifier, together.
 *
 * These tests mint signed tokens with a team-owned fixture and verify them
 * through the same production code path a real token takes. No container, no
 * socket, no auth service -- Sprint 8 replaces the issuer and none of this
 * changes.
 */
class JwtTokenVerifierTest {

    private static final String SECRET = "a-test-signing-secret-of-at-least-32-bytes";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtTokenVerifier verifier =
            new JwtTokenVerifier(new JwtProperties(SECRET, "HS256", "/api/v1/"));

    /** The team-owned fixture. It follows auth-api.yaml and is never deployed. */
    private static String mint(SecretKey key, long accountId, Instant expiry) {
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claims(Map.of("accountId", accountId, "email", "client@example.com"))
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("a well-formed, unexpired, correctly signed token yields its account id")
    void validTokenYieldsAccountId() {
        String token = mint(KEY, 3L, Instant.now().plusSeconds(600));

        assertThat(verifier.verifyAndExtractAccountId(token), is(3L));
    }

    @Test
    @DisplayName("a token signed with a different secret is refused")
    void forgedSignatureIsRefused() {
        SecretKey attackersKey = Keys.hmacShaKeyFor(
                "an-entirely-different-secret-32-bytes-long".getBytes(StandardCharsets.UTF_8));
        String forged = mint(attackersKey, 3L, Instant.now().plusSeconds(600));

        TokenVerificationException e = assertThrows(
                TokenVerificationException.class, () -> verifier.verifyAndExtractAccountId(forged));

        assertThat(e.reason(), is(TokenVerificationException.Reason.BAD_SIGNATURE));
    }

    @Test
    @DisplayName("an expired token is refused even though its signature is ours")
    void expiredTokenIsRefused() {
        String expired = mint(KEY, 3L, Instant.now().minusSeconds(60));

        TokenVerificationException e = assertThrows(
                TokenVerificationException.class, () -> verifier.verifyAndExtractAccountId(expired));

        assertThat(e.reason(), is(TokenVerificationException.Reason.EXPIRED));
    }

    @Test
    @DisplayName("an unsigned alg:none token is refused rather than trusted")
    void algNoneIsRefused() {
        // The classic forgery: a payload with no signature at all, asking the
        // verifier to take its word for the algorithm. A verifier that decoded
        // the payload first would already have believed this.
        String unsigned = Jwts.builder()
                .subject("3")
                .claims(Map.of("accountId", 3L))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .compact();

        assertThrows(TokenVerificationException.class,
                () -> verifier.verifyAndExtractAccountId(unsigned));
    }

    @Test
    @DisplayName("a token with no accountId claim is refused")
    void missingAccountClaimIsRefused() {
        String noClaim = Jwts.builder()
                .subject("3")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(KEY)
                .compact();

        TokenVerificationException e = assertThrows(
                TokenVerificationException.class, () -> verifier.verifyAndExtractAccountId(noClaim));

        assertThat(e.reason(), is(TokenVerificationException.Reason.NO_ACCOUNT_CLAIM));
    }

    @Test
    @DisplayName("a token that is not a JWT at all is refused")
    void malformedTokenIsRefused() {
        TokenVerificationException e = assertThrows(
                TokenVerificationException.class, () -> verifier.verifyAndExtractAccountId("not.a.jwt"));

        assertThat(e.reason(), is(TokenVerificationException.Reason.MALFORMED));
    }

    @Test
    @DisplayName("an absent token is refused without reaching the parser")
    void missingTokenIsRefused() {
        TokenVerificationException e = assertThrows(
                TokenVerificationException.class, () -> verifier.verifyAndExtractAccountId(null));

        assertThat(e.reason(), is(TokenVerificationException.Reason.MISSING));
    }

    @Test
    @DisplayName("a secret too short for HS256 fails at construction, not per request")
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenVerifier(new JwtProperties("too-short", "HS256", "/api/v1/")));
    }
}
