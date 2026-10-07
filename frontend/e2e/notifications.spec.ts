import { Page, expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Sprint 10 notifications, against the live stack: with the alert channel
 * set to the inbox, a market buy's outcome (executed or rejected, whichever
 * the executor decides) arrives in the inbox on its own, says it went to the
 * app, and the bell counts it until it is read.
 *
 * Leaves the channel on "In the app only": the seeded customers' addresses
 * are example.com, so email would only bounce in the team's mailbox.
 */
async function inAppOnly(page: Page): Promise<void> {
  await page.getByTestId('nav-settings').click();
  await page.getByTestId('settings-channel-inapp').check();
  await page.getByTestId('settings-save').click();
  await expect(page.getByTestId('settings-saved')).toBeVisible();
}

async function newest(page: Page): Promise<string | null> {
  await page.getByTestId('nav-notifications').click();
  await expect(page).toHaveURL(/\/notifications$/);
  await expect(page.getByTestId('notifications-list').or(page.getByTestId('notifications-empty'))).toBeVisible();
  const first = page.getByTestId('notification').first();
  return (await first.count()) === 0 ? null : first.getAttribute('data-id');
}

test.describe('notifications', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test('an order outcome arrives in the inbox on the channel Settings holds, and the bell counts it', async ({ page }) => {
    // The executor, the dispatcher and the inbox's own re-read, one after another.
    test.setTimeout(90_000);
    await page.goto('/dashboard');
    await signIn(page);
    await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');
    await inAppOnly(page);
    const before = await newest(page);

    await page.goto(`/trade?symbol=${encodeURIComponent(env.symbol)}&side=BUY`);
    await expect(page.getByTestId('ticket-type-market')).toBeChecked();
    await page.getByTestId('ticket-quantity').fill('1');
    await page.getByTestId('ticket-submit').click();
    await expect(page.getByTestId('ticket-status')).toHaveText(/^(NEW|FILLED|REJECTED)$/);

    await newest(page);
    // The page reads its history again every few seconds; the executor and the dispatcher take about as long.
    const latest = page.getByTestId('notification').first();
    if (before !== null) {
      await expect(latest).not.toHaveAttribute('data-id', before, { timeout: 45_000 });
    }
    const symbol = env.symbol.replace(/[.^$*+?()[\]{}|\\]/g, '\\$&');
    await expect(latest.getByTestId('notification-subject')).toHaveText(
      new RegExp(`^(Bought 1 ${symbol} at ₹|Order rejected: buy 1 ${symbol})`),
      { timeout: 45_000 },
    );
    await expect(latest.getByTestId('notification-delivery')).toHaveText('In the app', { timeout: 15_000 });
    await expect(page.getByTestId('bell-count')).toBeVisible();

    await latest.getByTestId('notification-read').click();
    await expect(latest.getByTestId('notification-read')).toHaveCount(0);
  });
});
