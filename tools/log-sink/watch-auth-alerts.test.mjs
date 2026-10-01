import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { readFileSync, rmSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { watch, ALERT_TAG } from './watch-auth-alerts.mjs';

function tmpPath(name) {
  return fileURLToPath(new URL(name, import.meta.url));
}

function run(lines, alertsLogPath) {
  return new Promise((resolve) => {
    const input = Readable.from(lines.map((l) => l + '\n'));
    const written = [];
    const out = { write: (s) => written.push(s) };
    const rl = watch(input, { alertsLogPath, out });
    rl.on('close', () => resolve(written));
  });
}

test('pages on a line containing the fixed tag, exactly once', async () => {
  const alertsLogPath = tmpPath('alerts.pages-once.test.log');
  if (existsSync(alertsLogPath)) rmSync(alertsLogPath);

  const written = await run(
    [
      'some unrelated INFO line',
      `WARN [RefreshTokenService] ${ALERT_TAG} credential=8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f revoked=2`,
      'another unrelated line',
    ],
    alertsLogPath,
  );

  assert.equal(written.length, 1);
  assert.match(written[0], new RegExp(ALERT_TAG));

  const persisted = readFileSync(alertsLogPath, 'utf8');
  assert.match(persisted, /PAGED on-call/);

  rmSync(alertsLogPath);
});

test('does not page on lines that only resemble the tag', async () => {
  const alertsLogPath = tmpPath('alerts.no-false-positive.test.log');
  if (existsSync(alertsLogPath)) rmSync(alertsLogPath);

  // pre-fix message shape, a truncated tag, wrong case -- none should match
  const written = await run(
    ['replayed refresh token for credential x', 'SECURITY_REFRESH_REPLA', 'security_refresh_replay'],
    alertsLogPath,
  );

  assert.equal(written.length, 0);
  assert.equal(existsSync(alertsLogPath), false);
});

test('the tag this sink matches is the one pinned in the auth service', () => {
  assert.equal(ALERT_TAG, 'SECURITY_REFRESH_REPLAY');
});
