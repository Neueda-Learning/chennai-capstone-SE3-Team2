package com.yellow.trade.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifies a token and answers who the caller is. Nothing else.
 *
 * ORDER MATTERS, and it is the order the brief assesses:
 *
 *   1. the signature   -- is this token ours at all?
 *   2. the expiry      -- is it still valid?
 *   3. the algorithm   -- is it the one we accept?
 *   ...and only then is any claim read.
 *
 * A verifier that decodes the payload first has already trusted whatever the
 * client sent. Steps 1 and 2 happen inside parseSignedClaims below, which
 * refuses the token before it hands back anything readable; step 3 is checked
 * against the header of the ALREADY-VERIFIED token, never against a header
 * read out of the raw string.
 *
 * Why step 3 exists at all when step 1 passed: algorithm confusion. A token
 * asking for "none" is not a signed JWT and parseSignedClaims rejects it. A
 * token asking for RS256 against an HMAC key is rejected here because
 * verifyWith(SecretKey) only admits MAC algorithms. Asserting the algorithm
 * explicitly means the guarantee survives someone later widening the key type
 * without thinking about it.
 */
@Component
public class JwtTokenVerifier {

    private final SecretKey key;
    private final String expectedAlgorithm;

    public JwtTokenVerifier(JwtProperties properties) {
        // HMAC-SHA256 needs at least 256 bits of key. A shorter secret is a
        // configuration mistake, and failing here at startup is far better
        // than failing per-request with a 500.
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 bytes for " + properties.algorithm());
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expectedAlgorithm = properties.algorithm();
    }

    /**
     * @return the numeric account key the token is for
     * @throws TokenVerificationException on any failure, with the reason on a
     *         typed field for the server log and nothing for the client
     */
    public long verifyAndExtractAccountId(String compactToken) {
        if (compactToken == null || compactToken.isBlank()) {
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.MISSING, "no token presented");
        }

        JwtParser parser = Jwts.parser()
                .verifyWith(key)     // step 1: signature, and MAC algorithms only
                .build();

        Jws<Claims> verified;
        try {
            // Signature and expiry are both checked here, before this returns
            // anything a caller could read.
            verified = parser.parseSignedClaims(compactToken);
        } catch (ExpiredJwtException e) {
            // step 2 failed
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.EXPIRED, "token expired at " + e.getClaims().getExpiration());
        } catch (SignatureException e) {
            // step 1 failed: forged, or signed with a different secret
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.BAD_SIGNATURE, "signature did not verify");
        } catch (UnsupportedJwtException e) {
            // an unsigned token, or one asking for an algorithm the key cannot serve
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.BAD_ALGORITHM, e.getMessage());
        } catch (MalformedJwtException | IllegalArgumentException e) {
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.MALFORMED, "token was not a well-formed JWS");
        }

        // step 3: the algorithm, read off the verified header rather than off
        // the untrusted string the client sent.
        JwsHeader header = verified.getHeader();
        if (!expectedAlgorithm.equals(header.getAlgorithm())) {
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.BAD_ALGORITHM,
                    "token requested " + header.getAlgorithm() + ", this service accepts " + expectedAlgorithm);
        }

        // Only now is a claim read.
        Claims claims = verified.getPayload();
        Object accountId = claims.get("accountId");
        if (accountId == null) {
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.NO_ACCOUNT_CLAIM,
                    "token carried no accountId claim");
        }
        if (accountId instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(accountId.toString());
        } catch (NumberFormatException e) {
            throw new TokenVerificationException(
                    TokenVerificationException.Reason.NO_ACCOUNT_CLAIM,
                    "accountId claim was not a number");
        }
    }
}
