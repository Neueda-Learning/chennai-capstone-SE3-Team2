/**
 * The two pages behind the activation link. Server-rendered and dependency
 * free: every value is escaped, and nothing is loaded from anywhere else.
 */

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

const STYLE = `
  body { font-family: system-ui, sans-serif; background: #f5f6f8; color: #1d2330; margin: 0; }
  main { max-width: 26rem; margin: 4rem auto; padding: 2rem; background: #fff; border-radius: 8px;
         border: 1px solid #dde1e8; }
  h1 { font-size: 1.35rem; margin: 0 0 1rem; }
  label { display: block; margin: 1rem 0 .25rem; font-weight: 600; }
  input { width: 100%; box-sizing: border-box; padding: .6rem; font-size: 1rem; border: 1px solid #b9c0cc; border-radius: 6px; }
  button { margin-top: 1.5rem; width: 100%; padding: .7rem; font-size: 1rem; border: 0; border-radius: 6px;
           background: #1f5fbf; color: #fff; cursor: pointer; }
  .hint { color: #5b6475; font-size: .9rem; }
  .error { color: #a3261a; background: #fdecea; padding: .6rem; border-radius: 6px; }
`;

function page(title: string, body: string): string {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${escapeHtml(title)}</title>
<style>${STYLE}</style>
</head>
<body><main>${body}</main></body>
</html>`;
}

export function registrationForm(token: string, error?: string, username = ''): string {
  const message = error ? `<p class="error">${escapeHtml(error)}</p>` : '';
  return page(
    'Activate your account',
    `<h1>Activate your account</h1>
${message}
<form method="post" action="/activate">
  <input type="hidden" name="token" value="${escapeHtml(token)}">
  <label for="username">Username</label>
  <input id="username" name="username" value="${escapeHtml(username)}" autocomplete="username"
         required minlength="3" maxlength="64" pattern="[A-Za-z0-9._\\-]+">
  <p class="hint">Letters, digits, dot, underscore and hyphen.</p>
  <label for="password">Password</label>
  <input id="password" name="password" type="password" autocomplete="new-password"
         required minlength="12" maxlength="128">
  <p class="hint">At least 12 characters. A long phrase is stronger than a short, complicated one.</p>
  <button type="submit">Create login</button>
</form>`,
  );
}

/** One page for every reason a link does not work, so it reveals none of them. */
export function invalidLinkPage(): string {
  return page(
    'Link not valid',
    `<h1>This link is not valid</h1>
<p>It may have expired, already been used, or been replaced by a newer email.
Contact support to have a new activation email sent.</p>`,
  );
}

/**
 * Where a successful registration lands. On this origin, because the form's
 * CSP is form-action 'self' and browsers apply it to the redirect after a
 * POST too: a redirect straight to the home page is silently blocked, the
 * customer sees nothing happen, and a second click finds the link used.
 */
export function activatedPage(homeUrl: string): string {
  return page(
    'Login created',
    `<h1>Your login is ready</h1>
<p>Your account is activated. Log in with the username and password you just chose.</p>
<p><a href="${escapeHtml(homeUrl)}">Go to Fauxnance and log in</a></p>`,
  );
}

export function errorPage(): string {
  return page('Something went wrong', `<h1>Something went wrong</h1><p>Please try again in a few minutes.</p>`);
}

/**
 * The token is in the URL, so the page must not pass it on: no Referer, no
 * caching, no framing, and a form that can only post back here.
 */
export const PAGE_HEADERS: Record<string, string> = {
  'Content-Type': 'text/html; charset=utf-8',
  'Cache-Control': 'no-store',
  'Referrer-Policy': 'no-referrer',
  'X-Content-Type-Options': 'nosniff',
  'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
};
