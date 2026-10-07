import { expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Sprint 10 advice, against the live stack: a stock's signal read from its
 * real daily candles, on request, with the sentence that produced it and the
 * statement that it is information, not advice.
 */
test('a stock page reads its signal on request: a view, the reason, and that it is not advice', async ({ page }) => {
  await page.goto('/dashboard');
  await signIn(page);
  await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');

  await page.goto(`/instrument/${encodeURIComponent(env.symbol)}`);
  await expect(page.getByTestId('advice-disclaimer')).toContainText('Information, not advice');
  await page.getByTestId('advice-read').click();

  await expect(page.getByTestId('advice-direction')).toHaveText(/^(BUY|SELL|HOLD)$/, { timeout: 20_000 });
  await expect(page.getByTestId('advice-reason')).not.toBeEmpty();
  await expect(page.getByTestId('advice-disclaimer')).toContainText('Information, not advice');
});
