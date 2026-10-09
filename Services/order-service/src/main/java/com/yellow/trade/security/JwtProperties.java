package com.yellow.trade.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, String algorithm, String issuer, String protectedPathPrefix) {
}
