import { Page, expect, test } from '@playwright/test';
import { env } from './env';
import { signIn } from './sign-in';

/**
 * Placing an order. Each test signs in afresh in its own browser context and
 * depends on no order another test placed.
 */
/** Searches for the instrument as a customer would, and picks the matching result. */
async function pickInstrument(page: Page, symbol: string): Promise<void> {
  // The ticket's own search; the market watch beside it has one too.
  await page.getByTestId('ticket-symbol').getByTestId('instrument-search').fill(symbol);
  await page.getByRole('option', { name: new RegExp(symbol.replace('.', '\\.')) }).first().click();
  await expect(page.getByTestId('ticket-instrument')).toBeVisible();
}

/** Clicks a radio's label, as a person does: the buttons are styled labels over hidden inputs. */
async function choose(page: Page, testId: string): Promise<void> {
  await page.locator('label', { has: page.getByTestId(testId) }).click();
  await expect(page.getByTestId(testId)).toBeChecked();
}

test.describe('place an order', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/trade');
    await signIn(page);
    await expect(page).toHaveURL(/\/trade$/);
  });

  test('the account is the one this sign-in may trade, and cannot be edited', async ({ page }) => {
    const account = page.getByTestId('ticket-account');

    // Shown as the reference the customer knows, ACC- and the key in six digits.
    await expect(account).toHaveValue(`ACC-${env.accountId.padStart(6, '0')}`);
    await expect(account).toHaveAttribute('readonly', '');
    await expect(account).not.toBeEditable();
  });

  test('an invalid order is stopped before it reaches the API', async ({ page }) => {
    let posted = 0;
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url() === `${env.tradeApi}/api/v1/orders`) {
        posted++;
      }
    });

    await pickInstrument(page, env.symbol);
    await choose(page, 'ticket-type-limit');
    await page.getByTestId('ticket-quantity').fill('0');
    await page.getByTestId('ticket-price').fill('10.555');
    await page.getByTestId('ticket-submit').click();

    await expect(page.getByText('Enter a whole number of units, 1 or more.')).toBeVisible();
    await expect(page.getByText('Enter a price above zero, with at most two decimal places.')).toBeVisible();
    expect(posted).toBe(0);
  });

  test('a placed order shows whatever status the API returned', async ({ page }) => {
    await pickInstrument(page, env.symbol);
    await choose(page, 'ticket-side-buy');
    await choose(page, 'ticket-type-limit');
    await page.getByTestId('ticket-quantity').fill('1');
    await page.getByTestId('ticket-price').fill('100.00');
    await page.getByTestId('ticket-submit').click();

    await expect(page.getByTestId('ticket-result')).toBeVisible();
    // NEW, FILLED or REJECTED are all a placed order. Asserting FILLED would
    // fail the week the executor is switched off -- the wrong signal.
    await expect(page.getByTestId('ticket-status')).toHaveText(/^(NEW|FILLED|REJECTED)$/);
  });
});
