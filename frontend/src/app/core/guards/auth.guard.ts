import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from '../session/session';

/**
 * Every route but sign-in runs this. A signed-out visitor is redirected to
 * sign-in, carrying where they were going -- never shown an empty screen,
 * because a user who has been told nothing will retry, then raise a ticket.
 *
 * A usability control, not a security control. The bundle is public and every
 * route in it is readable; authorisation is the Trade REST API's decision,
 * taken on every /api/v1/** call.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(Session).isSignedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/sign-in'], { queryParams: { returnUrl: state.url } });
};
