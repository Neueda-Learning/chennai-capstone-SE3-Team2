import { expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * The market watch and an instrument's page, against live prices: the watch
 * beside every signed-in screen, a stock added through the search, its chart,
 * and Buy opening the order window filled in. A wide window, where the watch
 * has room beside the page.
 */
test.describe('market watch', () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
    await signIn(page);
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('sits beside the dashboard, each row with a price from the platform', async ({ page }) => {
    const watch = page.getByRole('complementary', { name: 'Watchlist' });
    const rows = watch.getByTestId('watch-row');

    await expect(rows.first()).toBeVisible();
    // A price, or a dash when the source has none: never a blank.
    await expect(rows.first().locator('.quote')).toHaveText(/[\d,]+\.\d{2}|—/);
  });

  test('adds a stock found by search, and opens its chart and its order window', async ({ page }) => {
    const watch = page.getByRole('complementary', { name: 'Watchlist' });
    const symbol = env.symbol;

    // Out first, so the add is the journey's own whatever an earlier run left.
    const existing = watch.locator(`[data-testid="watch-row"][data-symbol="${symbol}"]`);
    if (await existing.count()) {
      // B, S and remove show on hover, as on Kite.
      await existing.hover();
      await existing.getByTestId('watch-remove').click();
      await expect(existing).toHaveCount(0);
    }
    await watch.getByTestId('instrument-search').fill(symbol);
    await watch.getByRole('option', { name: new RegExp(symbol.replace('.', '\\.')) }).first().click();
    const row = watch.locator(`[data-testid="watch-row"][data-symbol="${symbol}"]`);
    await expect(row).toBeVisible();

    await row.getByTestId('watch-open').click();
    await expect(page).toHaveURL(new RegExp(`/instrument/${symbol.replace('.', '\\.')}$`));
    await expect(page.getByTestId('instrument-title')).toHaveText(symbol);
    await expect(page.getByTestId('instrument-price')).toContainText('₹');
    // The chart is drawn on a canvas by the chart library, loaded on demand.
    await expect(page.getByTestId('candle-chart').locator('canvas').first()).toBeVisible();

    await page.getByTestId('range-1Y').click();
    await expect(page.getByTestId('range-1Y')).toHaveAttribute('aria-pressed', 'true');

    await page.getByTestId('instrument-buy').click();
    await expect(page).toHaveURL(/\/trade\?symbol=/);
    await expect(page.getByTestId('ticket-instrument')).toContainText(symbol);
    await expect(page.getByTestId('ticket-side-buy')).toBeChecked();
  });
});
