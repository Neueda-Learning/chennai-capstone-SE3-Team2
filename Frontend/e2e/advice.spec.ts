import { expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Sprint 10 advice, against the live stack: a stock's signal read from its
 * real daily candles, on request, with the sentence that produced it and the
 * statement that it is information, not advice; and the Signals page, a view
 * on everything the customer holds and watches.
 */
test('a stock page reads its signal on request: a view, the reason, and that it is not advice', async ({ page }) => {
  await page.goto('/dashboard');
  await signIn(page);
  await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');

  await page.goto(`/instrument/${encodeURIComponent(env.symbol)}`);
  await expect(page.getByTestId('advice-disclaimer')).toContainText('Information, not advice');
  await page.getByTestId('advice-read').click();

  await expect(page.getByTestId('advice-direction')).toHaveText(/^(BUY|SELL|HOLD|No signal)$/, { timeout: 20_000 });
  await expect(page.getByTestId('advice-reason')).not.toBeEmpty();
  await expect(page.getByTestId('advice-disclaimer')).toContainText('Information, not advice');
});

test('the Signals page gives every stock held or watched a view and its reason, or says why there is none', async ({ page }) => {
  await page.goto('/dashboard');
  await signIn(page);
  await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');

  await page.getByTestId('nav-signals').click();
  await expect(page).toHaveURL(/\/signals$/);
  await expect(page.getByTestId('signals-disclaimer')).toContainText('Information, not advice');
  const rows = page.getByTestId('signal-row');
  // Each new stock's candles are a price-service call; the first read can take a while.
  await expect(rows.first()).toBeVisible({ timeout: 60_000 });

  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    await expect(rows.nth(i).getByTestId('signal-direction')).toHaveText(/^(BUY|SELL|HOLD|No signal)$/);
    await expect(rows.nth(i).getByTestId('signal-reason')).not.toBeEmpty();
    await expect(rows.nth(i).getByTestId('signal-from')).toHaveText(/Held|Watched/);
  }
});
