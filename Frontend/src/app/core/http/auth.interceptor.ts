import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_CONFIG, ApiConfig } from '../config/api-config';
import { Session } from '../session/session';

/**
 * The only place in this application that sets an Authorization header.
 * Registered once, in app.config.ts.
 *
 * The token is a bearer credential: whoever holds it is the customer until it
 * expires. So it goes to our own APIs and to nothing else, decided by an
 * ALLOW LIST of origin-and-path pairs built from the configured API origins.
 * A host nobody listed -- the market-data API, an analytics script, a CDN --
 * gets no token, which is the safe way to fail: a deny list of hosts fails
 * open, silently, the day somebody adds one it does not name.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(Session).accessToken();
  if (token !== null && carriesToken(request.url, inject(API_CONFIG))) {
    return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(request);
};

interface TokenRoute {
  readonly origin: string;
  /** A path ending in '/' matches everything below it; any other path matches exactly. */
  readonly path: string;
}

/**
 * Where the token may go. The Trade REST API's protected routes, and the one
 * protected Auth route. /auth/login, /auth/register and /auth/refresh are
 * `security: []` in the contract and take no header, so they are not listed.
 */
function tokenRoutes(config: ApiConfig): readonly TokenRoute[] {
  return [
    { origin: new URL(config.tradeApiUrl).origin, path: '/api/v1/' },
    { origin: new URL(config.authApiUrl).origin, path: '/auth/me' },
  ];
}

/** Whether a request to this URL may carry the bearer token. */
export function carriesToken(url: string, config: ApiConfig): boolean {
  let target: URL;
  try {
    // Relative URLs resolve against this page, which is not on the list.
    target = new URL(url, document.baseURI);
  } catch {
    return false;
  }
  return tokenRoutes(config).some(
    (route) =>
      target.origin === route.origin &&
      (route.path.endsWith('/') ? target.pathname.startsWith(route.path) : target.pathname === route.path),
  );
}
