/**
 * The platform error catalogue. Every failure leaves as one of these,
 * in the envelope and nothing else.
 */
export const ErrorCode = {
  /** Unknown user, wrong password, expired token, bad signature, malformed header. */
  AUTH_401: 'AUTH-401',
  /** The username is already registered. */
  AUTH_409: 'AUTH-409',
  /** A field failed validation. */
  VAL_422: 'VAL-422',
  /** Internal route only: no provisioned account for this client. */
  ACT_404: 'ACT-404',
  /** Internal route only: the account already has a login, so no token is minted. */
  ACT_409: 'ACT-409',
} as const;

/** One message for every AUTH-401 cause: a helpful message names which half was wrong. */
export const UNAUTHORISED_MESSAGE = 'Unauthorised';
