# Source code walkthrough

Use this page to answer "how does X work?" with the code open. For each feature, the page gives:

1. **Show it in the UI**: what to click.
2. **Code path**: each function in order, from the button to the table, with `file:line`.
3. **Key code**: the lines to put on screen.
4. **Review questions**: "why", "what else", and the core concept.

Paths are short: `F/` = `frontend/src/app/`, `T/` = `services/trade-api/src/main/java/com/yellow/trade/`, `E/` = `services/trade-executor/src/main/java/com/yellow/executor/`, `A/` = `services/auth/src/`, `D/` = `libs/domain/src/main/java/com/yellow/`. In an IDE, open a class with Ctrl+N (IntelliJ) or Ctrl+P (VS Code) and go to the line.

Sequence diagrams for each flow: [`docs/architecture/sequences/`](../architecture/sequences/README.md).

---

## 1. How a request is authenticated

### Show it in the UI

Sign in. Open the browser developer tools, Network tab. Open the dashboard. Each `/api/v1/` request has `Authorization: Bearer eyJ...`. A request to another host has no such header.

### Code path

| # | Layer | Function | File:line | What it does |
|---|---|---|---|---|
| 1 | UI | `SignIn.submit()` | `F/features/sign-in/sign-in.ts:46` | Calls `AuthApi.signIn()`, then `Session.start()` |
| 2 | Auth | `AuthController.login()` | `A/auth/auth.controller.ts:45` | `@UseGuards(LoginThrottleGuard)` first |
| 3 | Auth | `AuthService.login()` | `A/auth/auth.service.ts:55` | `findByUsername`, argon2 `verify`, or dummy hash |
| 4 | Auth | `AccessTokenService.issue()` | `A/tokens/access-token.service.ts:12` | HS256, 900 s, six claims |
| 5 | UI | `Session.start()` | `F/core/session/session.ts:73` | Keeps tokens in `sessionStorage` |
| 6 | UI | `authInterceptor` | `F/core/http/auth.interceptor.ts:17` | Adds Bearer only for the allow list |
| 7 | API | `JwtAuthenticationFilter.doFilterInternal()` | `T/security/JwtAuthenticationFilter.java:36` | Reads the header, rejects with `401` |
| 8 | API | `JwtTokenVerifier.verifyAndExtractAccountId()` | `T/security/JwtTokenVerifier.java:40` | Signature, expiry, issuer, algorithm |
| 9 | API | `AccountAccess.requireOwn()` | `T/security/AccountAccess.java:31` | Path account = token account, else `403` |
| 10 | UI | `SessionRefresh` | `F/core/session/session-refresh.ts` | 60 s before expiry: `/auth/refresh`, one at a time |

### Key code

```ts
// F/core/http/auth.interceptor.ts:17
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(Session).accessToken();
  if (token !== null && carriesToken(request.url, inject(API_CONFIG))) {
    return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(request);
};
```

### Review questions

- **Why an allow list in the interceptor?** A deny list fails open the day someone adds a host. An allow list fails closed.
- **Why read `alg` from the verified header?** An `alg: none` token is refused before any claim is read.
- **What else could you do?** RS256 with a public key if many services verify. An `HttpOnly` cookie for the refresh token to reduce XSS exposure.
- **Core concept (NestJS):** a *guard* (`CanActivate`) runs before the handler and decides yes or no. `LoginThrottleGuard` and `JwtAuthGuard` are guards. A *pipe* validates the DTO (`ValidationPipe` with `forbidNonWhitelisted`). An *exception filter* (`AllExceptionsFilter`) makes the one error envelope.

---

## 2. How an account is created

### Show it in the UI

Landing page, **Open an account**. Fill in the form with a real email. Submit. About 40 s later the activation email arrives. Open the link, choose a username and password, then sign in.

### Code path

| # | Layer | Function | File:line | Table |
|---|---|---|---|---|
| 1 | UI | `Apply.submit()` | `F/features/apply/apply.ts:43` | |
| 2 | UI | `OnboardingApi.apply()` | `F/core/api/onboarding-api.ts:14` | |
| 3 | API | `OnboardingController.apply()` | `T/onboarding/OnboardingController.java:41` | |
| 4 | API | `ApplicationRateLimiter.tryAcquire()` | `T/onboarding/ApplicationRateLimiter.java:40` | |
| 5 | API | `OnboardingService.apply()` | `T/onboarding/OnboardingService.java:40` | `client_account`, `client_profile`, `bank_account` |
| 6 | API | `KycVerificationService.submit()` | `T/kyc/KycVerificationService.java:20` | `kyc_verification` PENDING |
| 7 | API job | `KycVerificationJob.run()` | `T/kyc/KycVerificationJob.java:46` | reads due rows |
| 8 | API | `KycDecider.decide()` | `T/kyc/KycDecider.java:66` | `kyc_verification`, `client_account.kyc_status` |
| 9 | API | `KycDecider.queueVerifiedEvent()` | `T/kyc/KycDecider.java:109` | `outbox_event` |
| 10 | API job | `OutboxRelay.relayOnce()` | `T/outbox/OutboxRelay.java:60` | Kafka `kyc-events` |
| 11 | Auth | `KycEventsConsumer.handle()` | `A/provisioning/kyc-events.consumer.ts:42` | |
| 12 | Auth | `ProvisioningService.provision()` | `A/provisioning/provisioning.service.ts:27` | `provisioned_account`, auth `outbox_event` |
| 13 | Auth | `OutboxRelay.relayOnce()` | `A/provisioning/outbox-relay.ts:39` | Kafka `account-provisioning` |
| 14 | API | `AccountProvisionedListener.onAccountProvisioned()` | `T/activation/AccountProvisionedListener.java:29` | |
| 15 | API | `ActivationService.handle()` | `T/activation/ActivationService.java:42` | `activation_email` |
| 16 | API | `AuthTokenClient.mintToken()` | `T/activation/AuthTokenClient.java:41` | |
| 17 | Auth | `InternalController.mint()` -> `ActivationTokenService.mint()` | `A/activation/internal.controller.ts:19`, `activation-token.service.ts:31` | `activation_token` |
| 18 | API | `SmtpActivationMailSender.send()` | `T/activation/SmtpActivationMailSender.java:22` | |
| 19 | Auth | `ActivationPageController.submit()` | `A/activation/activation-page.controller.ts:44` | |
| 20 | Auth | `AuthService.register()` -> `CredentialRepository.registerWithActivationToken()` | `A/auth/auth.service.ts:34`, `A/credentials/credential.repository.ts:60` | `activation_token`, `provisioned_account`, `credential` |

### Key code

```java
// T/kyc/KycDecider.java:66  (one transaction: decision + event)
if (kyc.decide(clientId, status.name(), verdict.reason(), write(verdict.checks())) == 0) {
    return Optional.empty();                       // another run decided first
}
if (kyc.setAccountKycStatus(clientId, status.name()) == 0) {
    throw new IllegalStateException(...);          // rolls the decision back
}
if (status == KycStatus.VERIFIED) {
    queueVerifiedEvent(clientId);                  // outbox row, same transaction
}
```

```ts
// A/provisioning/provisioning.service.ts:27
const inserted = await client.query(
  `INSERT INTO provisioned_account (account_id) VALUES ($1)
   ON CONFLICT (account_id) DO NOTHING`, [clientId]);
if (inserted.rowCount !== 1) { await client.query('ROLLBACK'); return false; }
await this.outbox.add(client, Topics.ACCOUNT_PROVISIONING, String(clientId), accountProvisioned(clientId));
await client.query('COMMIT');
```

### Review questions

- **Why does a duplicate PAN get `202`?** A different answer tells an attacker that the PAN belongs to a customer.
- **Why an outbox?** A publish inside the transaction can announce a rollback. A publish after it can be lost. The outbox commits the event with the change.
- **Why not put the activation token on Kafka?** It is a credential. Kafka keeps messages for days and anyone with broker access can read them.
- **What else?** A real KYC vendor behind the `KycProvider` interface. A shared rate-limit store.
- **Core concept:** *at-least-once* delivery plus *idempotent* consumer (`ON CONFLICT DO NOTHING`, `activation_email.event_id` PK).

---

## 3. How order placement works

### Show it in the UI

Watchlist, click **B** on `MRF.NS` (or open **Place an order**). Choose Buy, quantity 1, limit 130000. Submit. The answer is `NEW`. The blotter shows `FILLED` a few seconds later.

### Code path

| # | Layer | Function | File:line | Table or topic |
|---|---|---|---|---|
| 1 | UI | `OrderTicket.submit()` | `F/features/order-ticket/order-ticket.ts:271` | |
| 2 | UI | `TradeApi.placeOrder()` | `F/core/api/trade-api.ts:23` | |
| 3 | API | `JwtAuthenticationFilter.doFilterInternal()` | `T/security/JwtAuthenticationFilter.java:36` | |
| 4 | API | `OrderController.placeOrder()` | `T/controllers/OrderController.java:28` | `@Valid` |
| 5 | API | `OrderService.placeOrder()` | `T/services/OrderService.java:79` | `client_account` read |
| 6 | Domain | `OrderService.placeOrder()` (rules 1 to 8) | `D/services/OrderService.java:38` | `orders` INSERT |
| 7 | API | `OrderService.blockFunds()` (BUY) | `T/services/OrderService.java:119` | `client_account.blocked_funds` |
| 8 | API | `OrderEventPublisher.publishOrderPlaced()` | `T/services/OrderEventPublisher.java:40` | Kafka `orders` |
| 9 | Executor | `OrderPlacedConsumer.onOrderPlaced()` | `E/consume/OrderPlacedConsumer.java:33` | |
| 10 | Executor | `OrderExecutionService.execute()` | `E/consume/OrderExecutionService.java:79` | |
| 11 | Executor | `PreTradeChecks.beforePricing()`, `atExecution()` | `E/checks/PreTradeChecks.java:22`, `:39` | |
| 12 | Executor | `EquityFillRule.decide()` | `E/fill/EquityFillRule.java:11` | |
| 13 | Executor | `FullSettlement.settle()` | `E/settle/FullSettlement.java:44` | `orders`, `client_account`, `position` |
| 14 | Executor | `OrderExecutionService.publishTradeEvent()` | `E/consume/OrderExecutionService.java:165` | Kafka `trade-events` |
| 15 | UI | `Blotter.refresh()` re-reads every 3 s for a short burst | `F/features/blotter/blotter.ts:124` | |

### Key code

```ts
// F/features/order-ticket/order-ticket.ts:271  (same key on a retry of the same order)
const fingerprint = JSON.stringify(this.atMarket() ? { ...order, price: 'MARKET' } : order);
const idempotencyKey = this.unconfirmed?.order === fingerprint ? this.unconfirmed.key : crypto.randomUUID();
this.result.set(await this.tradeApi.placeOrder({ ...order, idempotencyKey }));
```

```java
// T/services/OrderService.java:79
Order order = domainOrderService.placeOrder(request);           // rules 1-8, INSERT orders NEW
if (order.side() == OrderSide.BUY) {
    blockFunds(account, money(order.quantity().multiply(order.limitPrice())));
}
applicationEventPublisher.publishEvent(new OrderPlacedDomainEvent(this, order));  // sent AFTER_COMMIT
```

```java
// E/settle/FullSettlement.java:44  (the guard that makes a duplicate harmless)
int affected = mapper.settleIfNew(order.orderId(), status, fillPrice, now, reason);
if (affected == 0) return SettlementResult.ALREADY_SETTLED;
```

### Review questions

- **Why Kafka between accept and execute?** Execution is slow and can fail. The API stays fast. The executor can restart without loss.
- **Why key `accountId`?** Kafka keeps order inside one partition. A SELL comes after the BUY that funded it.
- **Why block funds?** Two orders could each pass the funds check and together spend more than the balance.
- **Why optimistic lock, not `SELECT FOR UPDATE`?** Conflicts are rare. No lock is held during the request.
- **What else?** Move `ORDER_PLACED` to the outbox (U1). Reserve shares for a SELL (U3).
- **Core concept:** *idempotency key* at the API, *guarded state transition* in the executor.

---

## 4. How cancel works

### Show it in the UI

Place a BUY with a limit far below the market (it stays `NEW`). In **Orders**, click **Cancel**. Status changes to `CANCELLED`. Available cash goes back up. A notification arrives.

### Code path

| # | Layer | Function | File:line | Table |
|---|---|---|---|---|
| 1 | UI | `Blotter.cancel()` | `F/features/blotter/blotter.ts:110` | |
| 2 | UI | `TradeApi.cancelOrder()` (strips `ORD-`) | `F/core/api/trade-api.ts:32` | |
| 3 | API | `OrderController.cancelOrder()` | `T/controllers/OrderController.java:33` | |
| 4 | API | `OrderService.cancelOrder()` | `T/services/OrderService.java:200` | |
| 5 | API | `OrderMapper.cancelIfNew` | `services/trade-api/src/main/resources/mapper/OrderMapper.xml:125` | `orders` |
| 6 | API | `OrderService.releaseFunds()` (BUY) | `T/services/OrderService.java:131` | `client_account` |
| 7 | API | `OrderCancelledAnnouncer.on()` | `T/outbox/OrderCancelledAnnouncer.java:49` | `outbox_event` |
| 8 | API job | `OutboxRelay.relayOnce()` | `T/outbox/OutboxRelay.java:60` | Kafka `trade-events` |

### Key code

```xml
<!-- OrderMapper.xml:125 -->
UPDATE orders
   SET status = 'CANCELLED', resolved_at = #{resolvedAt}
 WHERE order_id = #{orderId}
   AND status   = 'NEW'
```

### Review questions

- **What if the executor fills it at the same moment?** Both updates are guarded on `NEW`. Postgres lets one win. The loser gets 0 rows.
- **Why release funds after the update, not before?** A cancel that lost the race must not release money that the fill used.

---

## 5. How the watchlist works

### Show it in the UI

**Watchlist** panel. Search `TCS`, pick it. The row appears at once. Prices update every 15 s. Reload the page: the list is still there (it is on the server).

### Code path

| # | Layer | Function | File:line | Table |
|---|---|---|---|---|
| 1 | UI | `MarketWatch.add()` | `F/features/market-watch/market-watch.ts:100` | |
| 2 | UI | `MarketWatchList.add()` (optimistic row) | `F/core/market/market-watch-list.ts:131` | |
| 3 | UI | `WatchlistsApi.addItem()` | `F/core/api/watchlists-api.ts:25` | |
| 4 | API | `WatchlistsController.addItem()` | `T/watchlists/WatchlistsController.java:62` | |
| 5 | API | `WatchlistService.addItem()` | `T/watchlists/WatchlistService.java:79` | `watch_item` |
| 6 | Executor | `MarketDataPoller.runCycle()` | `E/poller/MarketDataPoller.java:84` | view `watch_polled_symbols` |
| 7 | Executor | `MarketDataPoller.publish()` | `E/poller/MarketDataPoller.java:136` | Kafka `market-data` |
| 8 | API | `MarketDataListener.onQuote()` | `T/watchlists/MarketDataListener.java:29` | |
| 9 | API | `QuoteEvaluator.evaluate()` -> `LatestQuoteMapper.hold()` | `T/watchlists/QuoteEvaluator.java:47` | `watch_latest_quote` |

### Key code

```java
// T/watchlists/WatchlistService.java:79
access.requireOwn(accountId);
owned(accountId, watchlistId);                       // 404 WCH-404 if not this customer's
InstrumentRow instrument = instruments.findBySymbol(symbol);
watchlists.lockAccount(accountId);                   // pg_advisory_xact_lock(1013, clientId)
if (watchlists.hasItem(watchlistId, instrument.getInstrumentId())) return;
if (watchlists.countItems(watchlistId) >= WatchLimits.ITEMS_PER_WATCHLIST) throw new LimitReachedException(...);
watchlists.insertItem(watchlistId, instrument.getInstrumentId());
```

### Review questions

- **Why an advisory lock?** Two parallel requests cannot both pass "count < 50".
- **Why does the poller read a view?** The executor must not depend on the module's tables (decision 0009).
- **What else?** Server push (SSE or WebSocket) instead of a 15 s poll.

---

## 6. How price alerts work

### Show it in the UI

Open an instrument page. Set an alert ABOVE a price just under the current price. Wait for the next poll. The alert shows `TRIGGERED`. The bell count goes up. Click **Re-arm**.

### Code path

| # | Layer | Function | File:line | Table |
|---|---|---|---|---|
| 1 | UI | `InstrumentPage` -> `WatchlistsApi.setAlert()` | `F/features/instrument/instrument-page.ts:106`, `F/core/api/watchlists-api.ts:38` | |
| 2 | API | `AlertsController.create()` -> `AlertService.create()` | `T/watchlists/AlertsController.java:37`, `T/watchlists/AlertService.java:52` | `watch_alert` |
| 3 | API | `QuoteEvaluator.evaluate()` | `T/watchlists/QuoteEvaluator.java:47` | `watch_latest_quote` |
| 4 | API | `AlertMapper.lockCrossed()` | `T/watchlists/AlertMapper.java` | `watch_alert` `FOR UPDATE SKIP LOCKED` |
| 5 | API | `NotificationLedger.deliver()` (seam) | `T/notifications/NotificationLedger.java:57` | `notif_notification` |
| 6 | API | `AlertMapper.markTriggered()` | `T/watchlists/AlertMapper.java` | `watch_alert` |
| 7 | UI | `Alerts.rearm()` -> `AlertService.rearm()` | `F/features/alerts/alerts.ts:63`, `T/watchlists/AlertService.java:88` | `watch_alert` |

### Key code

```java
// T/notifications/NotificationLedger.java:57
@Transactional(propagation = Propagation.MANDATORY)   // refuses a call with no transaction
public DeliveryReceipt deliver(AlertNotice notice) { ... }
```

### Review questions

- **Why fire once?** Firing on every quote past the level floods the inbox.
- **Why `MANDATORY`?** An alert must never show `TRIGGERED` with no message queued.
- **Why key `(event_id, alert_id)`?** One quote can cross many alerts. `event_id` alone keeps only the first.

---

## 7. How notifications work for each order

### Show it in the UI

Place an order. Within a few seconds the bell shows `1`. Open the inbox. The message says "Bought 1 MRF at ...". If Settings says EMAIL, the email arrives too.

### Code path

| # | Layer | Function | File:line | Table |
|---|---|---|---|---|
| 1 | Executor | `OrderExecutionService.publishTradeEvent()` | `E/consume/OrderExecutionService.java:165` | Kafka `trade-events` |
| 2 | API | `TradeEventsListener.onTradeEvent()` | `T/notifications/TradeEventsListener.java:30` | |
| 3 | API | `NotificationLedger.record()` | `T/notifications/NotificationLedger.java:39` | `notif_notification` QUEUED |
| 4 | API job | `NotificationDispatchJob.runOnce()` | `T/notifications/NotificationDispatchJob.java:35` | |
| 5 | API | `NotificationDispatcher.dispatchOnce()` | `T/notifications/NotificationDispatcher.java:59` | `lockDue` |
| 6 | API | `NotificationDispatcher.send()` | `T/notifications/NotificationDispatcher.java:71` | |
| 7 | API | `ProfileChannelResolver.resolve()` (seam) | `T/preferences/ProfileChannelResolver.java:32` | `pref_preference`, `client_profile` |
| 8 | API | `SmtpNotificationMailSender.send()` | `T/notifications/SmtpNotificationMailSender.java:34` | |
| 9 | UI | `NotificationBell` constructor, `Inbox.refresh()` | `F/shared/notification-bell/notification-bell.ts:43`, `F/core/notifications/inbox.ts:17` | |
| 10 | UI | `Notifications.markRead()` | `F/features/notifications/notifications.ts:85` | `read_at` |

### Review questions

- **Why a ledger and a separate dispatcher?** A slow mail server must not stop a Kafka partition. The offset commits when the row is `QUEUED`.
- **Why its own thread?** A slow SMTP call must not delay the KYC job on the shared scheduler.
- **Why does the new group start at `latest`?** Old trades must not be mailed again.
- **What else?** Claim, commit, then send outside the transaction (U8). A stable `Message-ID` to stop a double email (U7).

---

## 8. How preferences work

### Show it in the UI

**Settings**. Choose landing screen **Holdings** and channel **In-app**. Save. Sign out and sign in: you land on Holdings. The next order notification is not emailed.

### Code path

| # | Layer | Function | File:line | Table |
|---|---|---|---|---|
| 1 | UI | `Settings.save()` | `F/features/settings/settings.ts:69` | |
| 2 | UI | `PreferencesApi.save()` | `F/core/api/preferences-api.ts:17` | |
| 3 | API | `PreferencesController.save()` | `T/preferences/PreferencesController.java:35` | |
| 4 | API | `PreferenceService.save()` | `T/preferences/PreferenceService.java:52` | `pref_preference` upsert |
| 5 | UI | `SignIn.submit()` -> `PreferencesApi.landingUrl()` | `F/core/api/preferences-api.ts:26` | |
| 6 | API | `ProfileChannelResolver.resolve()` | `T/preferences/ProfileChannelResolver.java:32` | read at send time |

### Review questions

- **Why not store the email in preferences?** One copy of personal data. No address that a customer types, so no SSRF.
- **Why `PUT` and not `POST`?** It replaces the whole resource. It is idempotent. CORS had to allow `PUT`.

---

## 9. How portfolio and P&L work

### Show it in the UI

**Holdings**: each holding with live value and unrealised P&L. Sell part of a holding. The realised P&L figure changes.

### Code path

| # | Layer | Function | File:line |
|---|---|---|---|
| 1 | UI | `Holdings` reads `PortfolioApi.positions()` and `summary()` | `F/features/holdings/holdings.ts:80` |
| 2 | API | `PortfolioService.summary()` | `T/portfolio/PortfolioService.java:64` |
| 3 | API | `Valuation.of()` with `PriceService.latest()` | `T/portfolio/Valuation.java:27` |
| 4 | API | `PortfolioTradeEventsListener.onTradeEvent()` -> `RealisedBook.book()` | `T/portfolio/RealisedBook.java:58` |

### Review questions

- **Why book realised P&L from events, not compute it?** The average cost at the moment of sale is gone once the position changes. The event carries it.
- **How do you stop a forged event?** `RealisedBook` checks `orders` for a FILLED SELL on that account.

---

## 10. How trade signals work

### Show it in the UI

**Signals**. Each held or watched stock shows BUY, SELL or HOLD, the strength, SMA20, SMA50, RSI14 and the disclaimer. On an instrument page, click **Read the signal**.

### Code path

| # | Layer | Function | File:line |
|---|---|---|---|
| 1 | UI | `Signals.refresh()` -> `AdviceApi.forAccount()` | `F/features/signals/signals.ts:45` |
| 2 | API | `AccountAdviceController.forAccount()` -> `AccountAdviceService.forAccount()` | `T/advice/AccountAdviceService.java:61` |
| 3 | API | `Holdings.heldSymbols()`, `WatchedInstruments.watchedSymbols()` (seams) | `T/portfolio/HoldingsReader.java`, `T/watchlists/WatchedInstrumentsReader.java` |
| 4 | API | `AdviceService.signal()` -> `compute()` | `T/advice/AdviceService.java:70`, `:105` |
| 5 | API | `Methodology.read()` | `T/advice/Methodology.java:39` |
| 6 | API job | `AdviceRefreshJob` -> `AdviceService.refreshAll()` every 5 min | `T/advice/AdviceService.java:92` |
| 7 | API | `AdviceMarketDataListener.onQuote()` -> `LatestPrices.offer()` | `T/advice/AdviceMarketDataListener.java:28` |

### Review questions

- **Why SMA crossover with RSI?** The most common and most explainable trend rule. RSI stops a BUY at an overbought peak.
- **Why no table?** A signal is computed from candles. It has no history that we must keep.
- **Why "information, not advice"?** A score that looks like a recommendation is a legal problem.

---

## 11. How an automated strategy works

### Show it in the UI

**Strategies**. Create: TCS, BUY 1, falls through a price just above the current price, max spend and max position. Switch it on. At the next quote, a run shows `PLACED`, then `FILLED`. The order is in the blotter.

### Code path

| # | Layer | Function | File:line | Table |
|---|---|---|---|---|
| 1 | UI | `Strategies.create()`, `toggle()` | `F/features/strategies/strategies.ts:160`, `:214` | |
| 2 | API | `StrategyController.create()` -> `StrategyService.create()` | `T/strategy/StrategyService.java:48` | `strat_strategy` |
| 3 | API | `StrategyListener.on()` | `T/strategy/StrategyListener.java:33` | |
| 4 | API | `StrategyTrigger.onQuote()` | `T/strategy/StrategyTrigger.java:37` | `findCrossed` |
| 5 | API | `StrategyFirer.fire()` | `T/strategy/StrategyFirer.java:59` | `strat_strategy FOR UPDATE`, `strat_run` |
| 6 | API | `HttpOrderPlacer.place()` | `T/strategy/HttpOrderPlacer.java:46` | |
| 7 | API | `StrategyTokenClient.mint()` | `T/strategy/StrategyTokenClient.java:37` | |
| 8 | Auth | `StrategyTokenController.mint()` -> `issueForStrategy()` | `A/strategy/strategy-token.controller.ts:29`, `A/tokens/access-token.service.ts:33` | |
| 9 | API | the public order route (section 3) | `T/services/OrderService.java:79` | `orders` |
| 10 | API | `StrategyOutcomes.apply()` | `T/strategy/StrategyOutcomes.java:40` | `strat_run` |

### Review questions

- **Why the public order route and not a direct insert?** All order checks apply: token, validation, funds, idempotency.
- **Why a 5-minute token?** It is used once at once. A longer life only makes a leak worse.
- **What stops a runaway strategy?** `maxSpend`, `maxPosition`, a stop after 3 failures, and the unique run key on the quote.
- **What else?** Commit the run, then call the route with no transaction open (U9). Skip stale quotes (U10).

---

## 12. Where to look for other things

| Topic | Place |
|---|---|
| Error envelope and codes | `T/controllers/GlobalExceptionHandler.java`, one handler per module (`*ExceptionHandler.java`) |
| Kafka error handling and DLT | `E/consume/ConsumerErrorHandling.java`, `T/*/...KafkaConfig.java` |
| Schema and constraints | `data/db/base/000_base_schema.sql`, `data/db/migrations/001` to `016` |
| Contracts | `contracts/*.yaml`, `services/trade-api/openapi/*.yaml` |
| Generated UI clients | `frontend/src/generated/`, wrapped by `F/core/api/*` |
| End-to-end tests | `frontend/e2e/*.spec.ts` (one for each journey) |
| Decision log | `docs/sprints/sprint-10/decision-log/` |
