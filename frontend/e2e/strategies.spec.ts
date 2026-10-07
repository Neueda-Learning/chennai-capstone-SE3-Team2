import { Page, expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Sprint 10 strategies, against the live stack. A buy of one share set on a
 * level the price is already above fires on the poller's next quote: the
 * platform places the order through POST /api/v1/orders with a token auth
 * minted for the account, the executor answers, and the outcome comes back
 * on trade-events. Nothing is stubbed, and the browser places nothing.
 */
function mine(page: Page) {
  return page.locator(`[data-testid="strategy"][data-symbol="${env.symbol}"]`);
}

/** An earlier run's strategy on the symbol would fire first: none left over. */
async function deleteLeftOvers(page: Page): Promise<void> {
  await expect(page.getByTestId('strategies-list').or(page.getByTestId('strategies-empty'))).toBeVisible();
  while ((await mine(page).count()) > 0) {
    const before = await mine(page).count();
    await mine(page).first().getByTestId('strategy-delete').click();
    await expect(mine(page)).toHaveCount(before - 1);
  }
}

test.describe('strategies', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test('a strategy switched on fires on the next quote through the order route, and its outcome comes back', async ({ page }) => {
    // The poller publishes about once a minute; the page reads every 15 seconds.
    test.setTimeout(240_000);
    await page.goto('/dashboard');
    await signIn(page);
    await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');

    await page.goto(`/instrument/${encodeURIComponent(env.symbol)}`);
    await expect(page.getByTestId('instrument-price')).toContainText('₹');
    const price = Number((await page.getByTestId('instrument-price').locator('.ltp').innerText()).replace(/[^\d.]/g, ''));

    await page.getByTestId('nav-strategies').click();
    await expect(page).toHaveURL(/\/strategies$/);
    await deleteLeftOvers(page);

    await page.getByLabel('Stock', { exact: true }).fill(env.symbol.replace(/\.NS$/, '').toLowerCase());
    await page.getByRole('option', { name: new RegExp(env.symbol.replace('.', '\\.')) }).first().click();
    await expect(page.getByTestId('strategy-picked')).toContainText(env.symbol);
    await page.getByTestId('strategy-side').selectOption('BUY');
    await page.getByTestId('strategy-quantity').fill('1');
    await page.getByTestId('strategy-trigger').selectOption('RISES_THROUGH');
    // Already above it: the next quote crosses.
    await page.getByTestId('strategy-price').fill(String(Math.max(1, Math.floor(price) - 1)));
    await page.getByTestId('strategy-max-spend').fill(String(Math.ceil(price * 1.1)));
    await page.getByTestId('strategy-max-position').fill('100000');
    await page.getByTestId('strategy-submit').click();
    await expect(page.getByTestId('strategy-created')).toContainText('Created, switched off');

    const strategy = mine(page).first();
    await expect(strategy.getByTestId('strategy-status')).toHaveText('Off');
    await strategy.getByTestId('strategy-toggle').click();
    await expect(strategy.getByTestId('strategy-status')).toHaveText('Armed');

    await expect(strategy).toHaveAttribute('data-status', 'FIRED', { timeout: 200_000 });
    await expect(strategy.getByTestId('strategy-status')).toContainText('Fired');

    await strategy.getByTestId('strategy-runs').click();
    await expect(strategy.locator('[data-testid="run"][data-outcome="PLACED"]')).toBeVisible();
    // The executor's answer, back on trade-events; the open runs are read again with the page.
    await expect(strategy.locator('[data-testid="run"][data-outcome="FILLED"], [data-testid="run"][data-outcome="REJECTED"]'))
      .toHaveCount(1, { timeout: 40_000 });

    await page.getByTestId('nav-orders').click();
    const executed = page.getByTestId('orders-executed').getByTestId('blotter-row').first();
    await expect(executed).toContainText(env.symbol);

    await page.getByTestId('nav-strategies').click();
    await deleteLeftOvers(page);
  });

  test('a moving-average crossover strategy shows the averages it waits on, read from the live price', async ({ page }) => {
    // The poller publishes about once a minute; the page reads every 15 seconds.
    test.setTimeout(180_000);
    await page.goto('/dashboard');
    await signIn(page);
    await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');
    await page.getByTestId('nav-strategies').click();
    await deleteLeftOvers(page);

    await page.getByLabel('Stock', { exact: true }).fill(env.symbol.replace(/\.NS$/, '').toLowerCase());
    await page.getByRole('option', { name: new RegExp(env.symbol.replace('.', '\\.')) }).first().click();
    await page.getByTestId('strategy-side').selectOption('BUY');
    await page.getByTestId('strategy-quantity').fill('1');
    await page.getByTestId('strategy-trigger').selectOption('MA_CROSSOVER');
    // An indicator fires on the averages: no price is asked for.
    await expect(page.getByTestId('strategy-price')).toHaveCount(0);
    await page.getByTestId('strategy-max-spend').fill('100000');
    await page.getByTestId('strategy-max-position').fill('100000');
    await page.getByTestId('strategy-submit').click();
    await expect(page.getByTestId('strategy-created')).toContainText('Created, switched off');

    const strategy = mine(page).first();
    await expect(strategy.getByTestId('strategy-rule')).toHaveText('Buy 1 when the 20-day average crosses above the 50-day');
    await strategy.getByTestId('strategy-toggle').click();
    await expect(strategy.getByTestId('strategy-status')).toHaveText(/Armed|Fired/);
    // The next quote reads the stock's daily history and today's price into both averages.
    await expect(strategy.getByTestId('strategy-indicator')).toContainText('20-day ₹', { timeout: 150_000 });
    await expect(strategy.getByTestId('strategy-indicator')).toContainText('50-day ₹');

    await deleteLeftOvers(page);
  });
});

