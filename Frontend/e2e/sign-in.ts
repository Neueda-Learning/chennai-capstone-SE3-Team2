import { Page } from '@playwright/test';
import { env } from './env';

/** Fills and submits the sign-in form, by its test identifiers. */
export async function signIn(page: Page, password: string = env.password): Promise<void> {
  await page.getByTestId('sign-in-username').fill(env.username);
  await page.getByTestId('sign-in-password').fill(password);
  await page.getByTestId('sign-in-submit').click();
}
