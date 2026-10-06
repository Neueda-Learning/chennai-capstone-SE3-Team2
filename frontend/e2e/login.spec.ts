import { Request, expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Signing in. Every test starts in a fresh browser context -- no token, no
 * storage -- and leaves nothing another test needs.
 */
test.describe('sign in', () => {
  test('a signed-out visitor sent to a guarded route is redirected to sign-in, carrying where they were going', async ({ page }) => {
    await page.goto('/trade');

    await expect(page).toHaveURL(/\/sign-in\?returnUrl=%2Ftrade$/);
    await expect(page.getByTestId('sign-in-needed')).toBeVisible();
    await expect(page.getByTestId('sign-in-submit')).toBeVisible();
  });

  test('a refused sign-in shows a readable error and stays signed out', async ({ page }) => {
    await page.goto('/sign-in');

    await signIn(page, `not-the-password-${Date.now()}`);

    await expect(page.getByTestId('error-message')).toContainText("That username and password don't match");
    await expect(page).toHaveURL(/\/sign-in/);
    await expect(page.getByTestId('sign-out')).toHaveCount(0);
  });

  test('a successful sign-in arrives where the visitor was going, and the token goes only to the platform', async ({ page }) => {
    const sent: Promise<{ url: string; authorization: string | undefined }>[] = [];
    page.on('request', (request: Request) => {
      sent.push(request.allHeaders().then((headers) => ({ url: request.url(), authorization: headers['authorization'] })));
    });

    await page.goto('/trade');
    await expect(page).toHaveURL(/returnUrl=%2Ftrade/);
    await signIn(page);

    await expect(page).toHaveURL(/\/trade$/);
    // The account reference the customer knows: ACC- and the key in six digits.
    await expect(page.getByTestId('ticket-account')).toHaveValue(`ACC-${env.accountId.padStart(6, '0')}`);

    // The dashboard calls both APIs, the protected auth route included.
    await page.getByRole('link', { name: 'Dashboard', exact: true }).click();
    await expect(page.getByTestId('signed-in-as')).toContainText(env.username);

    // The market watch keeps re-reading prices. Stop collecting before
    // waiting, so a re-read starting now is not left pending as the test ends.
    page.removeAllListeners('request');
    const requests = await Promise.all(sent);
    const login = requests.find((r) => r.url === `${env.authApi}/auth/login`);
    expect(login, 'the sign-in call went out').toBeDefined();
    expect(login?.authorization, '/auth/login takes no header').toBeUndefined();

    const withToken = requests.filter((r) => r.authorization !== undefined);
    expect(withToken.length).toBeGreaterThan(0);
    for (const request of withToken) {
      const url = new URL(request.url);
      const platform =
        (url.origin === env.tradeApi && url.pathname.startsWith('/api/v1/')) ||
        (url.origin === env.authApi && url.pathname === '/auth/me');
      expect(platform, `token sent to ${request.url}`).toBe(true);
      expect(request.authorization).toMatch(/^Bearer [\w-]+\.[\w-]+\.[\w-]+$/);
    }
  });
});
