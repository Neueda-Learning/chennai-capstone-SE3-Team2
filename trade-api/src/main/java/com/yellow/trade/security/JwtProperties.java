package com.yellow.trade.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the token verifier needs, bound from configuration.
 *
 * The secret has no default in application.yml, so the application refuses to
 * start when the environment does not supply JWT_SECRET. That is deliberate:
 * a default signing secret is a signing secret that reaches production, and a
 * service that starts anyway would accept tokens anybody could mint.
 *
 * `issuer` is required by auth-api.yaml: "Consumers must validate the
 * configured issuer." Without it, a token signed with the same shared secret by
 * any other service on the platform would be accepted here as a trading
 * credential.
 *
 * When Sprint 8 replaces the test fixture with the real auth service, these
 * values are the only thing that changes -- the same secret and issuer,
 * configured elsewhere. If any Java has to move, this service was coupled to an
 * issuer rather than to the token contract.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, String algorithm, String issuer, String protectedPathPrefix) {
}
