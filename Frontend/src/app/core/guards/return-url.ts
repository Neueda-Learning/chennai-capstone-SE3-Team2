/** Where to land after sign-in when there is no safe return address. */
export const DEFAULT_RETURN_URL = '/';

/**
 * The return address, accepted only if it is a path on this origin.
 *
 * `returnUrl` arrives in the query string, so whoever wrote the link chose it.
 * Followed unchecked, a sign-in page becomes an open redirect: a link to our
 * real login that drops the customer, signed in and trusting, on a lookalike
 * site. So anything that is not plainly one of our own paths -- an absolute
 * URL, a protocol-relative `//host`, a backslash trick, `javascript:` -- falls
 * back to the home page.
 */
export function safeReturnUrl(value: unknown, origin: string = window.location.origin): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return DEFAULT_RETURN_URL;
  }
  // Browsers drop tabs and newlines inside URLs, so "/\t/evil.example" would
  // otherwise become "//evil.example".
  if (/[\u0000-\u001f\u007f\\]/.test(value)) {
    return DEFAULT_RETURN_URL;
  }
  let target: URL;
  try {
    target = new URL(value, origin);
  } catch {
    return DEFAULT_RETURN_URL;
  }
  if (target.origin !== origin) {
    return DEFAULT_RETURN_URL;
  }
  const path = `${target.pathname}${target.search}${target.hash}`;
  // Returning to the sign-in page would only ask again.
  return target.pathname === '/sign-in' ? DEFAULT_RETURN_URL : path;
}
