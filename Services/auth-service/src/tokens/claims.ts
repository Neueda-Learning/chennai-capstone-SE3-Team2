/**
 * The access token payload, exactly as the contract fixes it.
 *
 * Six claims and no more. The payload is base64, not encryption: anything put
 * here is published to every holder of the token. And every claim is one a
 * consumer can start depending on, so removing one after Sprint 9 generates a
 * client from it is not a configuration change.
 */
export interface AccessTokenClaims {
  /** The user id, a UUID. Not the username, because a username can change. */
  sub: string;
  /** The numeric trading account key. What the Trade REST API compares against. */
  accountId: number;
  roles: string[];
  /** Issued at, seconds since the epoch. */
  iat: number;
  /** Expiry, seconds since the epoch. Fifteen minutes after iat. */
  exp: number;
  /** Who signed it. Consumers validate their configured issuer. */
  iss: string;
}

/** Used by the spec to assert that nothing else crept in. */
export const CONTRACT_CLAIMS = ['sub', 'accountId', 'roles', 'iat', 'exp', 'iss'] as const;

export const ACCESS_TOKEN_SECONDS = 900;

/**
 * Sprint 10: a token for an automated strategy, minted on an internal route
 * (decision log 0012). Five minutes: it is used at once, to place one order,
 * so anything longer only widens what a leak could do.
 */
export const STRATEGY_TOKEN_SECONDS = 300;

/** The role a strategy's token carries beside the customer's own, so its orders can be told apart. */
export const STRATEGY_ROLE = 'STRATEGY';
