# Log sink: paging on-call for a replayed refresh token

Watches the auth service's log output for `SECURITY_REFRESH_REPLAY` (see
A09 in `docs/security/sprint-08-auth-review.md`) and pages on-call the
moment it appears.

No real pager (PagerDuty, Opsgenie) is wired into this training platform,
so paging means an immutable record in `alerts.log` plus a terminal
banner. Swap `page()`'s body in `watch-auth-alerts.mjs` for a webhook call
to point this at a real provider.

## Running it

```bash
cd services/auth
npm run start | node ../../tools/log-sink/watch-auth-alerts.mjs
```

Reads stdin, so any source of the auth service's log lines works.

## Tests

```bash
node --test tools/log-sink/
```
