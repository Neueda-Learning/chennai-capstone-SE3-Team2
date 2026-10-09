import { defineConfig, devices } from '@playwright/test';
import { env } from './e2e/env';

/**
 * The journeys run against the real, running stack: the UI on E2E_BASE_URL
 * (`npm start`), the Trade REST API and the Auth service. Nothing is stubbed;
 * an end-to-end test that talks to a stub proves nothing about integration.
 *
 * One worker, and each file can run in its own process
 * (`npm run e2e:login`, `npm run e2e:order`): a journey that only passes when
 * its neighbour ran first should fail here, not in front of the instructor.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  timeout: 30_000,
  use: {
    baseURL: env.baseUrl,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
