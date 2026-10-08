# Flow 6: Preferences

A customer chooses three things in Settings: the default account, the landing screen after sign-in, and the alert channel (`EMAIL` or `IN_APP`). One row in `pref_preference` holds them. With no row, the documented defaults apply: own account, `dashboard`, `EMAIL` (decision 0004).

## A. Read and save Settings

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant ST as Settings component
        participant PA as PreferencesApi
    end
    box Trade REST API - preferences module
        participant PC as PreferencesController
        participant PS as PreferenceService
        participant AA as AccountAccess
        participant PM as PreferenceMapper
        participant PF as ProfileMapper
    end
    participant DB as trading DB

    C->>ST: open Settings
    ST->>PA: get(accountId)
    PA->>PC: GET /api/v1/accounts/{id}/preferences + Bearer
    PC->>PS: get(accountId) - readOnly transaction
    PS->>AA: requireOwn(accountId)
    PS->>PM: findByClient(accountId)
    PM->>DB: SELECT FROM pref_preference WHERE client_id = ?
    PS->>PS: view(accountId, row) - defaults when row is null
    opt channel EMAIL
        PS->>PF: findEmail(accountId)
        PF->>DB: SELECT email FROM client_profile
        PS->>PS: Masking.email() - "r***@gmail.com"
    end
    PS-->>ST: Preferences {landingScreen, alertChannel, contact masked, stored false}
    ST->>ST: show(preferences) - fill the form

    C->>ST: choose Holdings + IN_APP, click Save
    ST->>ST: save()
    ST->>PA: save(accountId, {defaultAccountId, landingScreen, alertChannel})
    PA->>PC: PUT /api/v1/accounts/{id}/preferences (CORS allows PUT since Sprint 10)
    PC->>PC: @Valid PreferencesUpdate (else 422 VAL-422)
    PC->>PS: save(accountId, update) - @Transactional
    PS->>AA: requireOwn(accountId)
    alt defaultAccountId is not accountId
        PS-->>ST: 403 ACC-403 (logged)
    end
    PS->>PM: upsert(PreferenceRow)
    PM->>DB: INSERT pref_preference ... ON CONFLICT (client_id) DO UPDATE
    PS-->>ST: Preferences {stored true, updatedAt}
```

## B. Where the preferences are used

```mermaid
sequenceDiagram
    autonumber
    participant SI as SignIn.submit()
    participant PA as PreferencesApi.landingUrl()
    participant API as PreferenceService.get()
    participant ND as NotificationDispatcher.send()
    participant CR as ProfileChannelResolver.resolve()
    participant DB as trading DB

    Note over SI,API: 1. Landing screen at sign-in
    SI->>PA: landingUrl(accountId)
    PA->>API: GET .../preferences
    API-->>PA: landingScreen = holdings
    PA-->>SI: "/holdings" (on any error: home, sign-in never fails)

    Note over ND,CR: 2. Alert channel at send time
    ND->>CR: resolve(accountId)
    CR->>DB: PreferenceMapper.findByClient()
    alt IN_APP
        CR-->>ND: ResolvedChannel(IN_APP, null)
    else EMAIL
        CR->>DB: ProfileMapper.findEmail()
        alt no address on profile
            CR-->>ND: ResolvedChannel(IN_APP, null) - inbox rather than nowhere
        else
            CR-->>ND: ResolvedChannel(EMAIL, address)
        end
    end
```

## Tables written in this flow

| Table | Writer | How |
|---|---|---|
| `pref_preference` | `PreferenceMapper.upsert` | One row for each customer. `CHECK (default_account_id = client_id)`. |

## Why it is built this way

- **The email is not copied into `pref_preference`** (decision 0003). It is read from `client_profile` at send time. There is one copy of personal data, and no address that a customer types. This designs out SSRF (A10).
- **`ResolvedChannel.toString()` leaves out the address.** A log line cannot show it.
- **The seam is a Java interface.** No HTTP route resolves a channel, so no customer token can call it.
