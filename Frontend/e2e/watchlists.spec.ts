import { Page, expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Sprint 10 watchlists and price alerts, against the live stack. The market
 * watch is kept on the server, so a stock added in one browser is there in
 * the next; and an alert set at the market price fires on the poller's next
 * quote, reads as fired, and arrives in the inbox. Nothing is stubbed: the
 * quote is the executor's, off market-data.
 */
const WATCHED = 'TATASTEEL.NS';

function watch(page: Page) {
  return page.getByRole('complementary', { name: 'Watchlist' });
}

async function signedIn(page: Page): Promise<void> {
  await page.goto('/dashboard');
  await signIn(page);
  await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');
  await expect(watch(page).getByTestId('watch-tab').first()).toBeVisible();
}

async function removeIfThere(page: Page, symbol: string): Promise<void> {
  const row = watch(page).locator(`[data-testid="watch-row"][data-symbol="${symbol}"]`);
  if (await row.count()) {
    await row.hover();
    await row.getByTestId('watch-remove').click();
    await expect(row).toHaveCount(0);
  }
}

test.describe('watchlists and price alerts', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test('the market watch is kept on the server: a stock added in one browser is there in the next', async ({ page, browser }) => {
    await signedIn(page);
    await removeIfThere(page, WATCHED);
    await watch(page).getByTestId('instrument-search').fill('tatasteel');
    await watch(page).getByRole('option', { name: /TATASTEEL\.NS/ }).first().click();
    await expect(watch(page).locator(`[data-testid="watch-row"][data-symbol="${WATCHED}"]`)).toBeVisible();

    // A second browser: nothing shared with the first but the account.
    const other = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const second = await other.newPage();
    await signedIn(second);
    await expect(watch(second).locator(`[data-testid="watch-row"][data-symbol="${WATCHED}"]`)).toBeVisible();

    await removeIfThere(second, WATCHED);
    await other.close();
    await page.reload();
    await expect(watch(page).getByTestId('watch-tab').first()).toBeVisible();
    await expect(watch(page).locator(`[data-testid="watch-row"][data-symbol="${WATCHED}"]`)).toHaveCount(0);
  });

  test('an alert at the market price fires on the next quote, reads as fired, and arrives in the inbox', async ({ page }) => {
    // The poller publishes about once a minute; the alerts page reads every 15 seconds.
    test.setTimeout(180_000);
    await signedIn(page);

    await page.goto(`/instrument/${encodeURIComponent(env.symbol)}`);
    await expect(page.getByTestId('instrument-price')).toContainText('₹');
    const price = Number((await page.getByTestId('instrument-price').locator('.ltp').innerText()).replace(/[^\d.]/g, ''));
    const threshold = Math.max(1, Math.floor(price) - 1);
    await page.getByTestId('alert-direction').selectOption('ABOVE');
    await page.getByTestId('alert-threshold').fill(String(threshold));
    await page.getByTestId('alert-submit').click();
    await expect(page.getByTestId('alert-set')).toContainText(`${env.symbol} rises to ₹${threshold}.00`);

    await page.getByTestId('nav-alerts').click();
    await expect(page).toHaveURL(/\/alerts$/);
    const alert = page.locator(`[data-testid="alert"][data-symbol="${env.symbol}"]`).first();
    await expect(alert.getByTestId('alert-condition')).toHaveText(`Rises to ₹${threshold.toLocaleString('en-IN')}.00`);
    await expect(alert).toHaveAttribute('data-status', 'TRIGGERED', { timeout: 150_000 });
    await expect(alert.getByTestId('alert-status')).toContainText('Fired at ₹');

    await alert.getByTestId('alert-notification').click();
    await expect(page).toHaveURL(/\/notifications$/);
    const newest = page.getByTestId('notification').first();
    await expect(newest).toHaveAttribute('data-kind', 'PRICE_ALERT', { timeout: 20_000 });
    await expect(newest.getByTestId('notification-subject')).toContainText(`${env.symbol} rose to ₹`);
  });
});
