import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse as AuthErrorResponse } from '../../../generated/auth';
import { ErrorResponse as ExtensionErrorResponse } from '../../../generated/extensions';
import { ErrorResponse as NotificationsErrorResponse } from '../../../generated/notifications';
import { ErrorResponse as StrategyErrorResponse } from '../../../generated/strategy';
import { ErrorResponse as TradeErrorResponse } from '../../../generated/trade';
import { ErrorResponse as WatchlistsErrorResponse } from '../../../generated/watchlists';

/** The codes each contract declares, read from the generated clients rather than typed out again. */
export type TradeErrorCode = TradeErrorResponse.ErrorCodeEnum;
export type AuthErrorCode = AuthErrorResponse.ErrorCodeEnum;
/** The codes our extension routes add: onboarding, instruments, payments, market data. */
export type ExtensionErrorCode = ExtensionErrorResponse.ErrorCodeEnum;
/** The codes the Sprint 10 notifications module adds. */
export type NotificationsErrorCode = NotificationsErrorResponse.ErrorCodeEnum;
/** The codes the Sprint 10 watchlists module adds. */
export type WatchlistsErrorCode = WatchlistsErrorResponse.ErrorCodeEnum;
/** The codes the Sprint 10 strategy module adds. */
export type StrategyErrorCode = StrategyErrorResponse.ErrorCodeEnum;

/**
 * Codes our services really send that neither contract lists: the login
 * throttle's AUTH-429 and the Trade REST API's catch-all SRV-500.
 */
export const OFF_CATALOGUE_CODES = ['AUTH-429', 'SRV-500'] as const;
export type OffCatalogueCode = (typeof OFF_CATALOGUE_CODES)[number];

export type KnownErrorCode =
  | TradeErrorCode
  | AuthErrorCode
  | ExtensionErrorCode
  | NotificationsErrorCode
  | WatchlistsErrorCode
  | StrategyErrorCode
  | OffCatalogueCode;

/**
 * One sentence a trader can act on, per code. A Record over the generated
 * code types, so this is a mapping with a completeness property: when a
 * contract gains a code and the clients are regenerated, this file stops
 * compiling until the new code has a sentence. Nobody extends a switch
 * statement after a customer reports a blank panel.
 *
 * Never the response's `message`: that is written for a developer reading a
 * log, and it changes without notice.
 */
export const ERROR_MESSAGES: Readonly<Record<KnownErrorCode, string>> = {
  'ACC-404': "We couldn't find that account. Sign in again, and if it still happens, contact support.",
  'ACC-403':
    "This account can't place orders right now. It may not be active or verified yet, or it isn't the account you signed in with.",
  'INS-404': "That instrument can't be traded. Check the symbol and try again.",
  'ORD-400': "There isn't enough cash in the account for this order. Lower the quantity or the price.",
  'ORD-409':
    "This order can't go through: you may not hold enough to sell, or it has already been placed. Check your orders before trying again.",
  'VAL-422': "Some of the details aren't acceptable. Check each field and try again.",
  'AUTH-401': 'Your session has expired or the sign-in was refused. Please sign in again.',
  'AUTH-409': 'That username is already taken. Choose another one.',
  'AUTH-429': 'Too many sign-in attempts. Wait a minute, then try again.',
  'PAY-400': "There isn't enough available cash for that withdrawal. Cash held for open orders can't be withdrawn.",
  'PAY-404': "There's no bank account registered on this account, so no money can move. Contact support.",
  'PAY-409': "That request was already used for a different transfer. Start a new one.",
  'RATE-429': 'Too many applications from this connection. Please try again in an hour.',
  'MKT-503': "Prices can't be fetched right now. Please try again in a few minutes.",
  'NTF-404': "That notification isn't on your account. Reload your notifications and try again.",
  'WCH-404': "That watchlist or alert isn't on your account any more. Reload the page and try again.",
  'LIM-409':
    "You've reached a limit: 5 watchlists, 50 instruments in each, 20 alerts waiting at once, and 10 strategies. Remove one to add another.",
  'STR-404': "That strategy isn't on your account any more. Reload the page and try again.",
  'SRV-500':
    'Something went wrong on our side. Your request may not have completed, so check your orders before trying again.',
};

/** Status 0: the browser never got a response. A service down, or a CORS rule that refuses this origin. */
export const UNREACHABLE_MESSAGE =
  "We couldn't reach the trading service. Check your connection; if it keeps happening, the service may be down.";

/** A code nobody has seen, or a response that is not the platform's envelope. */
export const UNKNOWN_ERROR_MESSAGE =
  'Something unexpected went wrong. Please try again, and if it keeps happening, contact support.';

export interface DisplayError {
  /** The platform code, for a support conversation; null when there was none. */
  readonly code: string | null;
  readonly message: string;
}

/**
 * What to tell the user about a failed call. Branches on `errorCode` only.
 *
 * @param overrides a screen's own wording for a code, where one sentence does
 *   not fit every screen -- AUTH-401 on the sign-in form means the password
 *   was wrong, not that a session expired. Still chosen by code.
 */
export function describeError(
  error: unknown,
  overrides: Partial<Record<KnownErrorCode, string>> = {},
): DisplayError {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return { code: null, message: UNREACHABLE_MESSAGE };
  }
  const code = errorCodeOf(error);
  if (code === null) {
    return { code: null, message: UNKNOWN_ERROR_MESSAGE };
  }
  if (isKnown(code)) {
    return { code, message: overrides[code] ?? ERROR_MESSAGES[code] };
  }
  return { code, message: UNKNOWN_ERROR_MESSAGE };
}

function errorCodeOf(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body: unknown = error.error;
  if (typeof body === 'object' && body !== null && 'errorCode' in body) {
    const code = (body as { errorCode: unknown }).errorCode;
    return typeof code === 'string' && code.length > 0 ? code : null;
  }
  return null;
}

function isKnown(code: string): code is KnownErrorCode {
  return Object.prototype.hasOwnProperty.call(ERROR_MESSAGES, code);
}
