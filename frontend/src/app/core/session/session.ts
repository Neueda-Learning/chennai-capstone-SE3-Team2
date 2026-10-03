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

/**
 * The signed-in session: the access token and what it says.
 *
 * Kept in sessionStorage, so a reload keeps the customer signed in and closing
 * the tab signs them out, and the token never outlives the browser session.
 * The signature is not checked here and cannot be -- the browser holds no
 * secret. Every API verifies the token on every call; this only reads it to
 * know which account to show and when the session has run out.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly token = signal<string | null>(readStored());

  /** The raw bearer token, for the interceptor. Null when signed out. */
  readonly accessToken = this.token.asReadonly();

  readonly claims = computed(() => {
    const token = this.token();
    return token === null ? null : decodeClaims(token);
  });

  /** The account this session may trade, from the token. */
  readonly accountId = computed(() => this.claims()?.accountId ?? null);

  /** Signed in with a token that has not yet expired. An expired one is cleared. */
  isSignedIn(): boolean {
    const claims = this.claims();
    if (claims === null) {
      return false;
    }
    if (claims.exp * 1000 <= Date.now()) {
      this.end();
      return false;
    }
    return true;
  }

  /** Starts a session from a fresh access token. Refuses one that is not a readable JWT. */
  start(accessToken: string): void {
    if (decodeClaims(accessToken) === null) {
      throw new Error('The sign-in response did not contain a usable access token');
    }
    this.token.set(accessToken);
    try {
      sessionStorage.setItem(STORAGE_KEY, accessToken);
    } catch {
      // Storage refused (private mode, quota): the session lasts until reload.
    }
  }

  /** Signs out: the token is gone from memory and from storage. */
  end(): void {
    this.token.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nothing stored to remove.
    }
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
