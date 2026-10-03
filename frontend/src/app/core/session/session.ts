import { Injectable, computed, signal } from '@angular/core';

/** What the access token says about this session. Read, never trusted: the APIs verify it. */
export interface SessionClaims {
  /** The credential id. */
  readonly sub: string;
  /** The one account this sign-in may trade. */
  readonly accountId: number;
  readonly roles: readonly string[];
  /** Expiry, seconds since the epoch. */
  readonly exp: number;
}

const STORAGE_KEY = 'trading-ui.accessToken';
const REFRESH_STORAGE_KEY = 'trading-ui.refreshToken';

/**
 * The signed-in session: the access token and what it says, and the refresh
 * token that renews it (SessionRefresh does that).
 *
 * Kept in sessionStorage, so a reload keeps the customer signed in and closing
 * the tab signs them out, and neither token outlives the browser session. The
 * refresh token is worth more than the access token -- days, not minutes -- but
 * the Auth service rotates it on every use and treats a second use as theft,
 * revoking every one the user has; and the contract hands it over in the body,
 * not as an HttpOnly cookie, so script can always reach it whatever we do.
 * The signature is not checked here and cannot be -- the browser holds no
 * secret. Every API verifies the token on every call; this only reads it to
 * know which account to show and when the session has run out.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly token = signal<string | null>(readStored());
  private refresh: string | null = readStoredRefresh();

  /** The raw bearer token, for the interceptor. Null when signed out. */
  readonly accessToken = this.token.asReadonly();

  readonly claims = computed(() => {
    const token = this.token();
    return token === null ? null : decodeClaims(token);
  });

  /** The account this session may trade, from the token. */
  readonly accountId = computed(() => this.claims()?.accountId ?? null);

  /**
   * Signed in with a token that has not yet expired. An expired one is
   * cleared; the refresh token is kept, so SessionRefresh can still renew it.
   */
  isSignedIn(): boolean {
    const claims = this.claims();
    if (claims === null) {
      return false;
    }
    if (claims.exp * 1000 <= Date.now()) {
      this.token.set(null);
      store(STORAGE_KEY, null);
      return false;
    }
    return true;
  }

  /** The refresh token, for SessionRefresh only. Null when there is none. */
  refreshToken(): string | null {
    return this.refresh;
  }

  /**
   * Starts a session from a fresh token pair: a sign-in, or a renewal. Refuses
   * an access token that is not a readable JWT.
   */
  start(accessToken: string, refreshToken: string | null = null): void {
    if (decodeClaims(accessToken) === null) {
      throw new Error('The sign-in response did not contain a usable access token');
    }
    this.token.set(accessToken);
    this.refresh = refreshToken;
    store(STORAGE_KEY, accessToken);
    store(REFRESH_STORAGE_KEY, refreshToken);
  }

  /** Signs out: both tokens are gone from memory and from storage. */
  end(): void {
    this.token.set(null);
    this.refresh = null;
    store(STORAGE_KEY, null);
    store(REFRESH_STORAGE_KEY, null);
  }
}

/** Writes or, for null, removes. Storage refused (private mode, quota): the session lasts until reload. */
function store(key: string, value: string | null): void {
  try {
    if (value === null) {
      sessionStorage.removeItem(key);
    } else {
      sessionStorage.setItem(key, value);
    }
  } catch {
    // Nothing more to do: memory still holds it.
  }
}

function readStored(): string | null {
  try {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    return stored !== null && decodeClaims(stored) !== null ? stored : null;
  } catch {
    return null;
  }
}

function readStoredRefresh(): string | null {
  try {
    return sessionStorage.getItem(REFRESH_STORAGE_KEY);
  } catch {
    return null;
  }
}

/** The JWT payload, if it has the claims a session needs; otherwise null. */
export function decodeClaims(token: string): SessionClaims | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)));
    const payload: unknown = JSON.parse(json);
    if (typeof payload !== 'object' || payload === null) {
      return null;
    }
    const { sub, accountId, roles, exp } = payload as Record<string, unknown>;
    if (
      typeof sub !== 'string' ||
      typeof accountId !== 'number' ||
      !Number.isSafeInteger(accountId) ||
      typeof exp !== 'number' ||
      !Array.isArray(roles)
    ) {
      return null;
    }
    return { sub, accountId, roles: roles.map(String), exp };
  } catch {
    return null;
  }
}
