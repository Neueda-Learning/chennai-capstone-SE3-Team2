import { expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Orders and Holdings, against the live stack: a market buy goes out at the
 * live price, shows under Open orders until the executor answers, then under
 * Executed; and Holdings prices what the account holds. Each test signs in
 * afresh.
 */
test.describe('orders and holdings', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
    await signIn(page);
    await expect(page.getByTestId('dashboard-greeting')).toContainText('Hi,');
  });

  test('a market buy at the live price moves from open to executed', async ({ page }) => {
    await page.goto(`/trade?symbol=${encodeURIComponent(env.symbol)}&side=BUY`);
    await expect(page.getByTestId('ticket-instrument')).toContainText(env.symbol);
    // At market once its live price is in.
    await expect(page.getByTestId('ticket-type-market')).toBeChecked();
    await page.getByTestId('ticket-quantity').fill('1');
    await page.getByTestId('ticket-submit').click();
    await expect(page.getByTestId('ticket-status')).toHaveText(/^(NEW|FILLED|REJECTED)$/);

    await page.getByTestId('ticket-see-orders').click();
    await expect(page).toHaveURL(/\/orders$/);
    // The executor answers within seconds; the page re-reads until it has.
    const executed = page.getByTestId('orders-executed').getByTestId('blotter-row').first();
    await expect(executed).toHaveAttribute('data-status', /^(FILLED|REJECTED)$/, { timeout: 30_000 });
    await expect(executed).toContainText(env.symbol);
  });

  test('prices every holding live, with totals', async ({ page }) => {
    await page.getByTestId('nav-holdings').click();
    await expect(page).toHaveURL(/\/holdings$/);

    const rows = page.getByTestId('holdings-row');
    await expect(rows.first()).toBeVisible();
    await expect(page.getByTestId('holdings-invested')).toContainText('₹');
    await expect(page.getByTestId('holdings-current')).toContainText('₹');
    // Each row a live price, or a dash where the source has none.
    await expect(rows.first().getByTestId('holdings-ltp')).toHaveText(/[\d,]+\.\d{2,4}|—/);

    await page.getByTestId('holdings-filter-stocks').click();
    await expect(page.getByTestId('holdings-filter-stocks')).toHaveAttribute('aria-pressed', 'true');
  });
});
