import { expect, test } from '@playwright/test';

/**
 * Applying for an account from the public page, against the live stack. The
 * application must reach the Trade REST API -- through the frontend
 * container's proxy when the UI is served by it, where POST
 * /onboarding/applications is the one public route outside /api.
 *
 * The PAN's digits are 0000, which the KYC stub refuses at its registry
 * check: no account is activated and no email is sent.
 */
const letter = () => String.fromCharCode(65 + Math.floor(Math.random() * 26));

test('an application sent from the public page is received by the platform', async ({ page }) => {
  await page.goto('/apply');

  await page.getByTestId('apply-name').fill('E2E Applicant');
  await page.getByTestId('apply-dob').fill('1990-05-17');
  await page.getByTestId('apply-email').fill(`e2e.apply.${Date.now()}@example.com`);
  await page.getByTestId('apply-mobile').fill('9812345611');
  await page.getByTestId('apply-pan').fill(`ABCP${letter()}0000${letter()}`);
  await page.getByTestId('apply-address').fill('12 Anna Nagar, Chennai');
  await page.getByTestId('apply-bank').fill('509876543210');
  await page.getByTestId('apply-ifsc').fill('DEMO0000001');
  await page.getByTestId('apply-submit').click();

  await expect(page.getByTestId('apply-received')).toContainText('Application received');
});
