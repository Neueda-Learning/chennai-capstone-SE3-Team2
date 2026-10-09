import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, InjectionToken, effect, inject, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { AuthApi } from '../api/auth-api';
import { Session, SessionClaims } from './session';

/** Renew this long before the access token expires, so no call goes out with a stale one. */
export const RENEW_BEFORE_MS = 60_000;
/** After a renewal that got no answer, try again this much later -- while the access token lasts. */
export const RETRY_MS = 30_000;

/** Runs `task` after `ms`; returns a cancel function. A seam for the specs. */
export const SESSION_TIMER = new InjectionToken<(task: () => void, ms: number) => () => void>('SESSION_TIMER', {
  providedIn: 'root',
  factory: () => (task, ms) => {
    const id = setTimeout(task, ms);
    return () => clearTimeout(id);
  },
});

/**
 * Keeps a signed-in customer signed in. The access token lasts 15 minutes;
 * a minute before it runs out, this exchanges the refresh token for a new
 * pair. Nobody is signed out in the middle of the demo.
 *
 * - **Refused** (401: the refresh token expired, was revoked, or was already
 *   used; 422: it was malformed): the session is over. It is ended and the customer is taken to
 *   sign-in, coming back to this page afterwards.
 * - **No answer**, or any other failure (the network, a 5xx): the session stays, and the renewal
 *   is tried again every 30 seconds while the access token lasts. After that
 *   the guard tries once more on the next navigation.
 *
 * One exchange at a time: the refresh token works once, so two exchanges
 * racing would present it twice -- which the Auth service treats as theft.
 */
@Injectable({ providedIn: 'root' })
export class SessionRefresh {
  private readonly session = inject(Session);
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly timer = inject(SESSION_TIMER);

  private inFlight: Promise<boolean> | null = null;
  private cancelTimer: (() => void) | null = null;

  constructor() {
    // Every new access token -- sign-in, renewal, a reload -- schedules the next renewal.
    effect(() => {
      const claims = this.session.claims();
      untracked(() => this.schedule(claims));
    });
  }

  /** Exchanges the refresh token now. True when the session was renewed. */
  renew(): Promise<boolean> {
    this.inFlight ??= this.exchange().finally(() => (this.inFlight = null));
    return this.inFlight;
  }

  private schedule(claims: SessionClaims | null, ms?: number): void {
    this.cancelTimer?.();
    this.cancelTimer = null;
    if (claims === null || this.session.refreshToken() === null) {
      return;
    }
    const due = ms ?? Math.max(claims.exp * 1000 - Date.now() - RENEW_BEFORE_MS, 0);
    this.cancelTimer = this.timer(() => {
      this.cancelTimer = null;
      void this.renewOnTime(claims);
    }, due);
  }

  private async renewOnTime(claims: SessionClaims): Promise<void> {
    if (await this.renew()) {
      return; // The new token's claims schedule the next one.
    }
    if (this.session.refreshToken() === null) {
      await this.router.navigate(['/sign-in'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (claims.exp * 1000 > Date.now() + RETRY_MS) {
      this.schedule(claims, RETRY_MS);
    }
  }

  private async exchange(): Promise<boolean> {
    const refreshToken = this.session.refreshToken();
    if (refreshToken === null) {
      return false;
    }
    try {
      const tokens = await this.authApi.refresh(refreshToken);
      this.session.start(tokens.accessToken, tokens.refreshToken);
      return true;
    } catch (failure) {
      if (failure instanceof HttpErrorResponse && (failure.status === 401 || failure.status === 422)) {
        this.session.end(); // Refused: this refresh token will never work again.
      }
      return false;
    }
  }
}
