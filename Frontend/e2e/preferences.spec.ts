import { Page, expect, test } from '@playwright/test';
import { signIn } from './sign-in';

/**
 * Sprint 10 preferences, against the live stack: a landing screen saved in
 * Settings is where the next sign-in opens. Puts the dashboard back after,
 * so no other journey depends on what this one chose.
 */
async function saveLanding(page: Page, screen: string, label: string): Promise<void> {
  await page.getByTestId('nav-settings').click();
  await expect(page).toHaveURL(/\/settings$/);
  await page.getByTestId('settings-landing').selectOption(screen);
  await page.getByTestId('settings-save').click();
  await expect(page.getByTestId('settings-saved')).toContainText(`Sign-in now opens on ${label}`);
}

test('a landing screen saved in Settings is where the next sign-in opens', async ({ page }) => {
  await page.goto('/sign-in');
  await signIn(page);
  await expect(page.getByTestId('nav-settings')).toBeVisible();

  await saveLanding(page, 'holdings', 'Holdings');
  await page.getByTestId('sign-out').click();
  await expect(page).toHaveURL(/\/sign-in/);

  await signIn(page);
  await expect(page).toHaveURL(/\/holdings$/);

  await saveLanding(page, 'dashboard', 'Dashboard');
});
