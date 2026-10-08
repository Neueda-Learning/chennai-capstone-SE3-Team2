# Flow 1: Create an account

From the application form to the first sign-in. The flow has four parts: (A) the customer gives the information, (B) the KYC check, (C) account provisioning and the activation email, (D) activation and first sign-in.

Each arrow names the function that runs. Each database arrow names the mapper method and the table.

## A. The customer gives the information

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant AP as Apply component
        participant OA as OnboardingApi
    end
    box Trade REST API
        participant OC as OnboardingController
        participant RL as ApplicationRateLimiter
        participant OS as OnboardingService
        participant OM as OnboardingMapper
        participant KS as KycVerificationService
        participant KM as KycMapper
    end
    participant DB as trading DB

    C->>AP: fill in name, dob, email, mobile, PAN, bank, IFSC
    AP->>AP: submit() - form validators, trim, upper-case PAN and IFSC
    AP->>OA: apply(ApplicationRequest)
    OA->>OC: POST /onboarding/applications (no token)
    OC->>OC: @Valid ApplicationRequest (else 422 VAL-422)
    OC->>RL: tryAcquire(remoteAddr)
    alt more than 5 in one hour from this IP
        RL-->>OC: false
        OC-->>AP: 429 RATE-429
    end
    OC->>OS: apply(request) - @Transactional
    OS->>OS: dateOfBirth(dob)
    OS->>OM: nextClientId()
    OM->>DB: SELECT nextval(client_account sequence)
    OS->>OM: insertAccount(clientId, ACC-000013, pan, dematId)
    OM->>DB: INSERT client_account ON CONFLICT DO NOTHING (status ACTIVE, kyc_status PENDING)
    OS->>OM: insertProfile(clientId, name, dob, email, phone, address)
    OM->>DB: INSERT client_profile ON CONFLICT DO NOTHING
    alt 0 rows (PAN or email already used)
        OS-->>OC: throw DuplicateApplicationException (transaction rolls back)
        OC-->>AP: 202 Accepted (same answer, no leak)
    end
    OS->>OM: insertBankAccount(clientId, accountNumber, ifsc, name)
    OM->>DB: INSERT bank_account
    OS->>KS: submit(clientId) - Propagation.MANDATORY
    KS->>KM: insertPending(clientId)
    KM->>DB: INSERT kyc_verification (status PENDING)
    OS-->>OC: clientId (commit)
    OC-->>OA: 202 Accepted {ApplicationReceived}
    OA-->>AP: resolve
    AP->>C: "Application received. Check your email."
```

## B. The KYC check

The check runs on a timer, not in the request. It waits `KYC_DELAY` (30 s) after the application.

```mermaid
sequenceDiagram
    autonumber
    box Trade REST API
        participant J as KycVerificationJob
        participant KD as KycDecider
        participant KR as KycRules
        participant SP as StubKycProvider
        participant KM as KycMapper
        participant OB as OutboxMapper
        participant RJ as OutboxRelayJob
        participant RE as OutboxRelay
    end
    participant DB as trading DB
    participant K as Kafka kyc-events

    loop every 10 s
        J->>J: run()
        J->>KM: findDue(cutoff = now - 30 s, maxAttempts 5, batch)
        KM->>DB: SELECT client_id FROM kyc_verification WHERE status = PENDING AND attempts < 5
        loop each clientId
            J->>KD: decide(clientId) - @Transactional
            KD->>KM: findApplicant(clientId)
            KM->>DB: SELECT client_account JOIN client_profile JOIN bank_account
            KD->>KR: isAdult(dob, today in IST)
            alt under 18
                KD->>KD: Verdict.fail(UNDER_AGE) - not sent to provider
            else adult
                KD->>SP: verify(Applicant pan, name, dob, bank, ifsc)
                SP-->>KD: Verdict passed or reason
            end
            KD->>KM: decide(clientId, VERIFIED or REJECTED, reason, checks json)
            KM->>DB: UPDATE kyc_verification SET status, decided_at WHERE status = PENDING
            alt 0 rows
                KD-->>J: Optional.empty (another run decided first)
            end
            KD->>KM: setAccountKycStatus(clientId, status)
            KM->>DB: UPDATE client_account SET kyc_status WHERE kyc_status = PENDING
            opt status = VERIFIED
                KD->>KD: queueVerifiedEvent(clientId) - EventEnvelope KYC_VERIFIED
                KD->>OB: insert(eventId, kyc-events, clientId, envelope)
                OB->>DB: INSERT outbox_event
            end
            KD-->>J: status (commit: decision and event together)
            opt exception
                J->>J: failed(clientId, e)
                J->>KM: recordFailure(clientId, exception class only)
                KM->>DB: UPDATE kyc_verification SET attempts + 1, last_error
            end
        end
    end

    loop every 2 s
        RJ->>RE: relayOnce() - @Transactional
        RE->>OB: lockBatch(20)
        OB->>DB: SELECT ... FROM outbox_event WHERE published_at IS NULL FOR UPDATE SKIP LOCKED
        RE->>K: KafkaTemplate.send(kyc-events, key clientId).get(timeout)
        alt broker ack
            RE->>OB: markPublished(eventId)
            OB->>DB: UPDATE outbox_event SET published_at = now()
        else error
            RE->>OB: recordFailure(eventId, error)
            OB->>DB: UPDATE outbox_event SET attempts + 1, last_error
            Note over RE: log OUTBOX_PUBLISH_FAILED, stop batch, retry next tick
        end
    end
```

## C. Account provisioning and the activation email

```mermaid
sequenceDiagram
    autonumber
    participant K1 as Kafka kyc-events
    box Auth service
        participant KC as KycEventsConsumer
        participant PS as ProvisioningService
        participant AO as auth OutboxRelay
        participant IG as InternalSecretGuard
        participant IC as InternalController
        participant TS as ActivationTokenService
        participant TR as ActivationTokenRepository
    end
    participant ADB as auth DB
    participant K2 as Kafka account-provisioning
    box Trade REST API
        participant AL as AccountProvisionedListener
        participant AS as ActivationService
        participant AM as ActivationMapper
        participant TC as AuthTokenClient
        participant MS as SmtpActivationMailSender
    end
    participant DB as trading DB
    participant G as Gmail SMTP

    K1->>KC: KYC_VERIFIED (group auth-provisioning)
    KC->>KC: handle() - interpretKycMessage(value)
    alt poison message
        KC->>K1: sendRaw(kyc-events.DLT, x-failure-* headers)
    end
    KC->>PS: provision(clientId)
    PS->>ADB: BEGIN
    PS->>ADB: INSERT provisioned_account (account_id) ON CONFLICT DO NOTHING
    alt rowCount = 0 (already provisioned)
        PS->>ADB: ROLLBACK - nothing published
    end
    PS->>ADB: OutboxRepository.add() INSERT outbox_event ACCOUNT_PROVISIONED
    PS->>ADB: COMMIT
    AO->>ADB: relayOnce() - lockBatch FOR UPDATE SKIP LOCKED
    AO->>K2: KafkaPublisher.send(account-provisioning, key clientId)
    AO->>ADB: markPublished(eventId)

    K2->>AL: ACCOUNT_PROVISIONED (group activation-mailer)
    AL->>AL: onAccountProvisioned(value) - AccountProvisionedEvent.parse()
    AL->>AS: handle(event)
    AS->>AM: countSent(eventId)
    AM->>DB: SELECT count(*) FROM activation_email WHERE event_id = ?
    alt already sent
        AS-->>AL: return (idempotent)
    end
    AS->>AM: findEmail(clientId)
    AM->>DB: SELECT email FROM client_profile
    AS->>TC: mintToken(clientId)
    TC->>IG: POST /internal/activation-tokens, header x-internal-secret
    IG->>IG: canActivate() - SHA-256 both sides, timingSafeEqual
    IG->>IC: mint(MintActivationTokenDto)
    IC->>TS: mint(clientId)
    TS->>TS: randomBytes(32).toString(hex), expires in 24 h
    TS->>TR: replaceFor(clientId, sha256(token), expiresAt)
    TR->>ADB: SELECT claimed_by FROM provisioned_account WHERE account_id = ? FOR UPDATE
    TR->>ADB: UPDATE activation_token SET revoked_at = now() (older live tokens)
    TR->>ADB: INSERT activation_token (token_hash, client_id, expires_at)
    IC-->>TC: 201 {activationToken, expiresAt} (plaintext only here)
    TC-->>AS: token
    AS->>AS: link(token) = ACTIVATION_LINK_BASE_URL?token=...
    AS->>MS: send(ActivationEmail(email, link))
    MS->>G: JavaMailSender.send()
    AS->>AM: recordSent(eventId, clientId)
    AM->>DB: INSERT activation_email (event_id PK)
```

## D. Activation and first sign-in

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Auth service
        participant PC as ActivationPageController
        participant AU as AuthService
        participant PH as PasswordHasher
        participant CR as CredentialRepository
        participant LG as LoginThrottleGuard
        participant AT as AccessTokenService
        participant RT as RefreshTokenService
    end
    participant ADB as auth DB
    participant R as Redis
    box Angular UI
        participant SI as SignIn component
        participant AA as AuthApi
        participant SE as Session
        participant PA as PreferencesApi
    end

    C->>PC: GET /activate?token=... (link in email)
    PC->>PC: show() - ActivationTokenService.isUsable(token)
    PC-->>C: form: username, password (CSP default-src none)
    C->>PC: POST /activate {token, username, password}
    PC->>PC: submit() - plainToInstance(RegisterDto) + validate()
    PC->>AU: register(dto)
    AU->>PH: hash(password) - argon2id 64 MiB, t=4, p=1
    AU->>CR: registerWithActivationToken(username, hash, sha256(token))
    CR->>ADB: BEGIN
    CR->>ADB: UPDATE activation_token SET used_at = now() WHERE hash = ? AND live
    CR->>ADB: UPDATE provisioned_account SET claimed_by WHERE claimed_by IS NULL
    CR->>ADB: INSERT credential (id, username, password_hash, account_id, roles CUSTOMER)
    CR->>ADB: COMMIT
    alt token bad, used, expired or claimed
        AU-->>PC: AUTH-401 - "This link is not valid"
    end
    PC-->>C: 303 See Other to /activate/done

    C->>SI: username + password
    SI->>SI: submit()
    SI->>AA: signIn(username, password)
    AA->>LG: POST /auth/login
    LG->>R: GET login-attempts:ip (blocked at 5)
    LG->>AU: login(dto)
    AU->>CR: findByUsername(username)
    CR->>ADB: SELECT FROM credential
    AU->>PH: verify(hash, password) (dummy hash if user unknown)
    alt wrong
        AU->>R: Lua INCR + PEXPIRE 60 s
        AU-->>SI: 401 AUTH-401 Unauthorised
    end
    AU->>AT: issue(credential) - HS256 JWT, 900 s, claims sub accountId roles iss
    AU->>RT: issue - INSERT refresh_token (sha256, 7 days)
    AU-->>AA: {accessToken, refreshToken, expiresIn 900}
    SI->>SE: start(accessToken, refreshToken) - sessionStorage
    SI->>PA: landingUrl(accountId)
    PA-->>SI: /dashboard (or the stored landing screen)
    SI->>C: navigateByUrl(target)
```

## Tables written in this flow

| Table | Database | Written by | Step |
|---|---|---|---|
| `client_account` | trading | `OnboardingMapper.insertAccount`, `KycMapper.setAccountKycStatus` | A, B |
| `client_profile` | trading | `OnboardingMapper.insertProfile` | A |
| `bank_account` | trading | `OnboardingMapper.insertBankAccount` | A |
| `kyc_verification` | trading | `KycMapper.insertPending`, `decide`, `recordFailure` | A, B |
| `outbox_event` | trading | `OutboxMapper.insert`, `markPublished` | B |
| `provisioned_account` | auth | `ProvisioningService.provision`, `CredentialRepository.registerWithActivationToken` | C, D |
| `outbox_event` | auth | `OutboxRepository.add`, auth `OutboxRelay` | C |
| `activation_token` | auth | `ActivationTokenRepository.replaceFor`, `registerWithActivationToken` | C, D |
| `activation_email` | trading | `ActivationMapper.recordSent` | C |
| `credential` | auth | `registerWithActivationToken` | D |
| `refresh_token` | auth | `RefreshTokenService` | D |

## Why it is built this way

- **A duplicate application gets the same `202`.** A different answer tells an attacker that a PAN or an email belongs to a customer.
- **The decision and the event commit in one transaction (outbox).** A broker outage delays the event. It never loses it.
- **The activation token never goes on Kafka.** It is a credential. It goes from auth to the mailer on an internal route only.
- **The account comes from the token, not from the request.** Knowing an account number is not sufficient to claim it.
