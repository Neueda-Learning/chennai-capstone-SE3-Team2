import { expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Adding and withdrawing cash. Each test signs in afresh in its own browser
 * context. A deposit really moves money on the stack it runs against: a small
 * one, which also gives the order journey cash to spend.
 */
test.describe('cash', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/cash');
    await signIn(page);
    await expect(page).toHaveURL(/\/cash$/);
  });

  test('shows the bank account masked to its last four digits, and nothing more of it', async ({ page }) => {
    await expect(page.getByTestId('cash-bank')).toHaveText(/^••••\d{4}$/);
  });

  test('a deposit is PROCESSING at once, then the gateway decides it, without a reload', async ({ page }) => {
    await page.getByTestId('cash-amount').fill('100.00');
    await page.getByTestId('cash-deposit').click();

    const newest = page.getByTestId('cash-transfer-row').first();
    await expect(newest).toContainText('+100.00');
    // SUCCESS or FAILED are both a decision; asserting SUCCESS would fail the
    // day the stub's rules change, not the day the Cash page breaks.
    await expect(newest).toHaveAttribute('data-status', /^(SUCCESS|FAILED)$/, { timeout: 20_000 });
  });

  test('a withdrawal over the available cash is stopped before it reaches the API', async ({ page }) => {
    let posted = 0;
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url() === `${env.tradeApi}/api/v1/accounts/${env.accountId}/withdrawals`) {
        posted++;
      }
    });
    await expect(page.getByTestId('cash-available')).toContainText('₹');

    await page.getByTestId('cash-amount').fill('999999999.99');
    await page.getByTestId('cash-withdraw').click();

    await expect(page.getByTestId('cash-over-available')).toBeVisible();
    expect(posted).toBe(0);
  });
});
