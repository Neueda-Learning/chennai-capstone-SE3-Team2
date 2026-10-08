# Flow 5: A notification for each order

Each order result on `trade-events` (`ORDER_FILLED`, `ORDER_REJECTED`, `ORDER_CANCELLED`) becomes one notification. The flow has two halves that do not wait for each other:

1. **Record.** The consumer writes a `QUEUED` row in the ledger, then commits the Kafka offset.
2. **Dispatch.** A separate thread sends due rows on the channel that preferences gives at send time.

A slow mail server never stops a Kafka partition (decision 0006).

## A. Record the event in the ledger

```mermaid
sequenceDiagram
    autonumber
    participant EX as Executor / OutboxRelay
    participant T as Kafka trade-events
    box Trade REST API - notifications module
        participant L as TradeEventsListener
        participant TE as TradeEvent
        participant NL as NotificationLedger
        participant NM as NotificationMessages
        participant MP as NotificationMapper
    end
    participant DB as trading DB

    EX->>T: ORDER_FILLED / ORDER_REJECTED / ORDER_CANCELLED, key accountId
    T->>L: message (group notification-service, starts at latest)
    L->>TE: onTradeEvent(value) -> TradeEvent.parse(value, json)
    alt not JSON, unknown eventType
        TE-->>L: exception (poison, to trade-events.DLT)
    end
    L->>NL: record(event) - @Transactional
    NL->>NM: forTrade(event) - subject + body ("Bought 10 TCS at 3,950.00")
    NL->>NL: row(eventId, alertId null, accountId, kind, message)
    NL->>MP: insertQueued(row)
    MP->>DB: INSERT notif_notification (status QUEUED) ON CONFLICT ON CONSTRAINT uq_notif_source DO NOTHING
    alt 0 rows (same eventId seen before)
        NL-->>L: false - replay, nothing queued twice
    end
    NL-->>L: true (commit)
    L->>T: acknowledge offset
```

## B. Dispatch on the customer's channel

```mermaid
sequenceDiagram
    autonumber
    box Trade REST API - notifications module
        participant J as NotificationDispatchJob (own thread)
        participant D as NotificationDispatcher
        participant MP as NotificationMapper
        participant MS as SmtpNotificationMailSender
    end
    box preferences module
        participant CR as ProfileChannelResolver (api.ChannelResolver)
        participant PM as PreferenceMapper
        participant PF as ProfileMapper
    end
    participant DB as trading DB
    participant G as Gmail SMTP

    loop every 2 s
        J->>D: runOnce() -> dispatchOnce() - @Transactional
        D->>MP: lockDue(now, batchSize)
        MP->>DB: SELECT FROM notif_notification WHERE status = QUEUED AND next_attempt_at <= now FOR UPDATE SKIP LOCKED
        loop each due row
            D->>D: send(row, now)
            D->>CR: resolve(clientId) - Java seam
            CR->>PM: findByClient(accountId)
            PM->>DB: SELECT FROM pref_preference
            opt channel EMAIL (or no row: default EMAIL)
                CR->>PF: findEmail(accountId)
                PF->>DB: SELECT email FROM client_profile
            end
            CR-->>D: ResolvedChannel(channel, destination, fromDefault)
            alt UnknownAccountException
                D->>MP: markFailed(id, "no such account")
            else resolver threw
                D->>D: failedAttempt() - stays QUEUED, never sent on a guess
            else IN_APP
                D->>MP: markSent(id, IN_APP, null, now)
                MP->>DB: UPDATE notif_notification SET status SENT, channel IN_APP
            else EMAIL
                D->>MS: send(to, subject, body)
                MS->>G: JavaMailSender.send()
                alt MailDeliveryException
                    D->>D: failedAttempt(row, EMAIL, masked, error)
                    alt attempts >= 5
                        D->>MP: markFailed() - status FAILED
                    else
                        D->>MP: retryLater(id, reason, now + 30 s x attempts)
                    end
                else accepted
                    D->>MP: markSent(id, EMAIL, Masking.email(address), now)
                    MP->>DB: UPDATE SET status SENT, destination masked
                end
            end
        end
    end
```

## C. The bell and the inbox in the UI

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant NB as NotificationBell
        participant IB as Inbox (store)
        participant NP as Notifications page
        participant NA as NotificationsApi
    end
    box Trade REST API
        participant NC as NotificationsController
        participant NS as NotificationService
        participant MP as NotificationMapper
    end
    participant DB as trading DB

    loop on sign-in, on each navigation, and every 30 s
        NB->>IB: refresh()
        IB->>NA: unread(accountId)
        NA->>NC: GET /api/v1/accounts/{id}/notifications/unread
        NC->>NS: unread(accountId) - AccountAccess.requireOwn()
        NS->>MP: countUnread(accountId)
        MP->>DB: SELECT count(*) WHERE client_id = ? AND read_at IS NULL
        NS-->>NB: UnreadCount -> badge "3"
    end
    C->>NP: open the inbox
    NP->>NA: history(accountId, 50)
    NA->>NC: GET .../notifications?limit=50
    NC->>NS: history() -> MP.findForClient()
    C->>NP: click a message
    NP->>NA: markRead(accountId, notificationId)
    NA->>NC: POST .../notifications/{nid}/read
    NC->>NS: markRead(accountId, nid)
    NS->>MP: markRead(accountId, nid, now)
    MP->>DB: UPDATE notif_notification SET read_at WHERE client_id = ? AND notification_id = ?
    alt 0 rows (another customer's id)
        NS-->>NP: 404 NTF-404
    end
```

## Notification states

```mermaid
stateDiagram-v2
    [*] --> QUEUED : NotificationLedger.record() or deliver()
    QUEUED --> SENT : email accepted, or IN_APP
    QUEUED --> QUEUED : mail refused, attempts + 1, wait 30 s x attempts
    QUEUED --> QUEUED : resolver did not answer
    QUEUED --> FAILED : 5th refusal, or account gone
    SENT --> [*]
    FAILED --> [*]
```

## Tables written in this flow

| Table | Writer | How |
|---|---|---|
| `notif_notification` | `NotificationMapper.insertQueued` | `UNIQUE NULLS NOT DISTINCT (event_id, alert_id)` makes a replay a no-op |
| `notif_notification` | `markSent`, `retryLater`, `markFailed` | Under `FOR UPDATE SKIP LOCKED` |
| `notif_notification.read_at` | `markRead` | Only the customer's own rows |

## Why it is built this way

- **Ledger first, send later.** The Kafka offset commits when the row is `QUEUED`, not when Gmail answers.
- **New group starts at `latest`.** A new deployment does not mail a month of old trades.
- **The channel is read at send time.** A change in Settings applies to the next message, also one already queued.
- **The address is stored masked.** A full email address never goes in a log or in the row.
