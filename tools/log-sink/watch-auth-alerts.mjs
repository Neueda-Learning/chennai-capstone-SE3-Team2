#!/usr/bin/env node
// Pages on-call when the auth service logs a replayed refresh token.
// No real pager is wired into this training platform, so paging means an
// immutable record in alerts.log plus a terminal banner. Swap page()'s
// body for a webhook call to point this at a real provider.
//
// Usage: pipe the auth service's log output into this script's stdin.

import { createInterface } from 'node:readline';
import { appendFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

export const ALERT_TAG = 'SECURITY_REFRESH_REPLAY';

const HERE = dirname(fileURLToPath(import.meta.url));
export const ALERTS_LOG_PATH = join(HERE, 'alerts.log');

export function page(line, { alertsLogPath = ALERTS_LOG_PATH, out = process.stderr } = {}) {
  appendFileSync(alertsLogPath, `${new Date().toISOString()} PAGED on-call: ${line}\n`);
  out.write(`PAGING ON-CALL: ${line}\n`);
}

export function watch(input = process.stdin, options = {}) {
  const rl = createInterface({ input, terminal: false });
  rl.on('line', (line) => {
    if (line.includes(ALERT_TAG)) page(line, options);
  });
  return rl;
}

// Only run when executed directly, not when imported by the test.
if (import.meta.url === `file://${process.argv[1]}`) {
  watch();
}
