# Sequence diagrams

One file for each flow. Each diagram shows the function that runs at each step and the table that each database call reads or writes. The `.mermaid` file beside each `.md` holds the same diagrams for a Mermaid viewer.

| # | Flow | File | Main tables |
|---|---|---|---|
| 1 | Create an account: information, KYC check, provisioning and activation email, first sign-in | [01-create-account.md](01-create-account.md) | `client_account`, `client_profile`, `bank_account`, `kyc_verification`, `outbox_event`, `provisioned_account`, `activation_token`, `activation_email`, `credential` |
| 2 | Place an order, buy and sell, to execution | [02-place-order.md](02-place-order.md) | `orders`, `client_account`, `position` |
| 3 | Cancel an order | [03-cancel-order.md](03-cancel-order.md) | `orders`, `client_account`, `outbox_event` |
| 4 | Watchlist | [04-watchlist.md](04-watchlist.md) | `watch_list`, `watch_item`, `watch_latest_quote` |
| 5 | A notification for each order | [05-order-notifications.md](05-order-notifications.md) | `notif_notification` |
| 6 | Preferences | [06-preferences.md](06-preferences.md) | `pref_preference`, `client_profile` |
| 7 | Price alerts | [07-price-alerts.md](07-price-alerts.md) | `watch_alert`, `watch_latest_quote`, `notif_notification` |
| 8 | Trade signals (advice) | [08-signals.md](08-signals.md) | none written (memory only) |
| 9 | Automated strategy | [09-automated-strategy.md](09-automated-strategy.md) | `strat_strategy`, `strat_run`, then `orders` |

## Conventions

- `autonumber` gives each step a number. Use the number when you explain a step.
- `alt` shows the error or other path. `opt` shows a step that runs only sometimes. `loop` shows a timer or a repeat.
- `Note over` explains a transaction boundary or a rule.
- A participant name is the real class or component name in the code. Find it with a search for the name.

The system view is in [`../system-architecture.md`](../system-architecture.md).
