import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from '../session/session';
import { SessionRefresh } from '../session/session-refresh';

/**
 * Every route but sign-in runs this. A signed-out visitor is redirected to
 * sign-in, carrying where they were going -- never shown an empty screen,
 * because a user who has been told nothing will retry, then raise a ticket.
 *
 * A usability control, not a security control. The bundle is public and every
 * route in it is readable; authorisation is the Trade REST API's decision,
 * taken on every /api/v1/** call.
 *
 * An access token that ran out while the tab slept, or before a reload, is
 * renewed here first if the refresh token still works.
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  const session = inject(Session);
  const refresh = inject(SessionRefresh);
  const router = inject(Router);
  if (session.isSignedIn() || (await refresh.renew())) {
    return true;
  }
  return router.createUrlTree(['/sign-in'], { queryParams: { returnUrl: state.url } });
};
