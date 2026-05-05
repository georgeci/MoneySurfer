# Bank API Integration — Research & Design Draft

> Companion doc: [bank-statement-import.md](bank-statement-import.md) — file-based import (Track A, MVP). This document covers **Track B**: direct connections to banks, either via PSD2 aggregators (EU/UK) or licensed multi-region aggregators (RU). Scheduled for **Phase 2**, after Track A ships.

## TL;DR for agents

- **Phase 2 path**, not MVP. Track A ships first to deliver value while Phase 2 platform work lands.
- **EU / UK pilot provider**: [GoCardless Bank Account Data](https://gocardless.com/bank-account-data/) (formerly Nordigen) — free for EU institutions, ~2400 banks, lowest commercial risk. PSD2 AISP licence is GoCardless's, we're a TPP-client.
- **RU**: no public personal-account API exists at any major bank (Тинькофф/Т-Банк, Сбер, Альфа expose API only for **business** accounts). Only licensed path for individuals is **Salt Edge** (paid). Otherwise RU stays on Track A.
- **Architecture**: SCA-redirect via WebView → consent → background pull through Ktor → normalise → reuse Track A's `CommitImportUseCase` and `ImportBatch` aggregate. Both tracks land in the same domain pipeline.
- **Major blockers in current code**: no Ktor, no WebView, no encrypted storage, no iOS background tasks, no banking sub-package in `data-remote/`. All listed in [Platform gaps](#platform-gaps).
- **Out of scope**: payments / SCA-for-payments, KYC, on-boarding, account opening.

## Goals & non-goals

**Goals**

- One-time bank consent flow inside the app, then automatic transaction & balance pull on a schedule.
- Multiple connected banks per workspace, each surfaced as one or more accounts.
- Re-uses Track A's normalised pipeline — no parallel `Transaction` shape.
- Honest UI for consent expiry (PSD2: 90/180-day windows depending on RTS interpretation — recheck at implementation time).

**Non-goals**

- Initiating payments (PIS), only account information (AIS).
- Becoming an AISP ourselves — always go through a licensed aggregator.
- KYC / on-boarding flows — not a banking app.
- Real-time push from bank — pull is enough for v1.

## Provider landscape — EU / UK

All EU/UK options are PSD2 AISP-licensed aggregators that hide per-bank quirks behind one API.

| Provider | Coverage | Pricing | Consent window | KMP fit | Notes |
|---|---|---|---|---|---|
| **GoCardless Bank Account Data** (Nordigen) | EU + UK, ~2400 banks | **Free** for AIS in EU; commercial terms apply at scale | 90 days (PSD2 default), reauth flow | REST only, no SDK — fits Ktor cleanly | Recommended pilot. Free + biggest coverage. |
| TrueLayer | UK strong, EU growing | Paid, per-call / per-user | 90 days | REST + Android SDK (we'd ignore SDK in KMP) | Mature UK product, costlier. |
| Tink (Visa) | Pan-EU, broad | Enterprise pricing, contact sales | 90 days | REST | Big-co, friction to onboard. |
| Yapily | EU + UK headless | Paid | 90 days | REST, no UI SDK | "API-only" by design — fits us, but paid. |
| Salt Edge | Global incl. RU | Paid, tiered | Varies by region | REST + SDKs | Only path for RU; see RU section. |
| Klarna Kosma | EU | Paid | 90 days | REST | Decent coverage; commercial gating. |

**Decision driver**: start with GoCardless because (a) free, (b) widest EU coverage, (c) plain REST suits our KMP shape. Add Salt Edge later for RU only.

### PSD2 consent window note

PSD2 RTS originally pinned consent at 90 days with re-SCA. The 2024 RTS amendment proposed extending to 180 days for AIS and removing forced re-SCA where the user is "active". Implementation status varies by EBA guideline adoption per country. **Treat 90 days as the worst case**; query the provider for the actual remaining lifetime per connection. Build the reauth UX around `connection.expires_at`, not a hard-coded constant.

## Provider landscape — Russia / СНГ

Personal accounts:

| Bank | Public API for individuals? | Notes |
|---|---|---|
| Тинькофф / Т-Банк | ❌ | Public API exists only for business / расчётный счёт (Tinkoff Business). |
| Сбер | ❌ | Open API targets юрлиц / 1С integrations. |
| Альфа-Банк | ❌ | Open API for business / SME. |
| ВТБ, Райффайзен RU, Открытие, Газпромбанк | ❌ | Same. |
| Ozon Банк, Yandex Bank | ❌ | No public personal API. |

Routes available for RU individuals:

1. **Salt Edge** (recommended if budget allows) — licensed, supports several RU banks via screen-scraping or partner integration; legal coverage by Salt Edge's contracts. Paid.
2. **Track A — statement import** — covers RU end-to-end with zero legal exposure.
3. **Unofficial scrapers / unofficial libraries** — explicit non-goal. Violates bank ToS, fragile, security risk to user credentials. **Reject.**

Conclusion: for RU personal accounts, Track A is the default; Track B becomes available only if/when Salt Edge is brought in.

## Architecture sketch

```
┌──────────────┐
│ ConnectBank  │  (UI, feature/bank-connect)
│   screen     │
└──────┬───────┘
       │ list institutions (cached)
       ▼
┌──────────────┐
│ Provider     │  (data-remote/banking/<provider>/)
│ adapter      │  - Ktor REST client
│   (Go-       │  - DTOs + domain mappers
│   Cardless)  │  - normalises errors to DomainError
└──────┬───────┘
       │ start consent
       ▼
┌──────────────┐         ┌──────────────────────┐
│ WebView SCA  │ ◀──────▶│ Bank's auth page     │
│ redirect     │         │ (out of our control) │
└──────┬───────┘         └──────────────────────┘
       │ callback → consent_id
       ▼
┌──────────────┐
│ BankConn     │  (domain entity, persisted to Room + Firestore)
│ stored       │  status: PENDING / ACTIVE / EXPIRED / REVOKED
└──────┬───────┘
       │
       ▼ (foreground "refresh" or scheduled)
┌──────────────┐     ┌─────────────────────────┐
│ Refresh      │────▶│ ImportBatch (Track A)   │
│ use case     │     │ + CommitImportUseCase   │
└──────────────┘     └────────────┬────────────┘
                                  │
                                  ▼
                       Room → existing outbox → Firestore
```

Same sink as Track A: every transaction we pull turns into an `ImportBatch` with `source = TransactionSource.BANK_API`, then flows through the existing batch-insert use case. **No parallel write path.**

## Domain changes required

Track A introduces `TransactionSource`, `externalId`, `importBatchId`, `ImportBatch`, `Account.externalAccountId`, `Account.provider`, `CommitImportUseCase`, `RevertImportBatchUseCase` — see [bank-statement-import.md `Domain changes required`](bank-statement-import.md#domain-changes-required-textual). Track B reuses all of those and adds:

1. **`TransactionSource.BANK_API`** — already in Track A's enum, no change.
2. **`BankConnection` aggregate** in `domain/.../model/BankConnection.kt`:
   - `id: BankConnectionId`, `workspaceId`, `provider: String` (e.g. `"gocardless"`, `"saltedge"`), `institutionId: String`, `institutionName: String`, `status: BankConnectionStatus` (`PENDING`, `ACTIVE`, `EXPIRED`, `REVOKED`, `ERROR`), `consentId: String`, `expiresAt: Long?`, `lastSyncAt: Long?`, `createdAt: Long`.
3. **`BankInstitution`** value object — provider + id + name + country + logoUrl. Cached read-only data, not user data.
4. **Repositories** in `domain/.../repositories/`:
   - `BankConnectionRepository` — CRUD by workspace, status updates.
   - `BankInstitutionRepository` — list/search institutions per provider+country.
5. **Use cases** in `domain/.../usecase/`:
   - `ListBankInstitutionsUseCase` (provider, country) → list.
   - `StartBankConnectionUseCase` (provider, institutionId) → returns redirect URL + connection id.
   - `CompleteBankConnectionUseCase` (connectionId, callback params) → mark `ACTIVE`.
   - `RefreshBankConnectionUseCase` (connectionId) → calls provider, normalises rows, hands off to `CommitImportUseCase`.
   - `DisconnectBankUseCase` — revoke at provider, mark `REVOKED` locally; transactions stay (user can revert per batch via Track A's `RevertImportBatchUseCase`).
6. **Provider abstraction** in `domain/.../banking/`:
   - `BankProvider` interface — minimal surface (`listInstitutions`, `startConnection`, `completeConnection`, `fetchAccounts`, `fetchTransactions`, `disconnect`).
   - Implementations live in `data-remote/banking/gocardless/`, `data-remote/banking/saltedge/`. New sub-package, **not** under the Firebase path.
7. **Errors** — typed `BankingError` sealed hierarchy in `domain/.../error/`. Adapters convert provider errors. Per AGENTS.md: SDK errors crossing into domain are typed domain errors.
8. **Sync** — `BankConnection` syncs across devices (entity type `BANK_CONNECTION`), `consentId` is encrypted at write time (see [Secrets](#secrets-handling)). Tokens themselves never leave the device.

## Platform gaps

Items required specifically by Track B. Track A's gaps (file picker, CSV) are independent.

| Capability | State today | Track B requirement | Candidate |
|---|---|---|---|
| HTTP client | ❌ none (Firestore-only `data-remote/`) | KMP HTTP client with per-platform engine | Ktor (`ktor-client-core` + `ktor-client-okhttp` Android, `ktor-client-darwin` iOS, `ktor-client-cio` JVM). Add via `gradle/libs.versions.toml`. |
| WebView (KMP) | ❌ none | OAuth-style SCA redirect inside the app | [`compose-webview-multiplatform`](https://github.com/KevinnZou/compose-webview-multiplatform) (KevinnZou). Alternative: launch system browser via Custom Tabs (Android) / `SFSafariViewController` (iOS) and intercept callback URL — simpler, less control. |
| Secure storage | ❌ DataStore is unencrypted ([`data-local/.../datastore/DataStoreFactory.*`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/datastore/)) | Encrypted store for `consentId`, refresh tokens, provider client secrets if any | `expect`/`actual` `SecureStorage`: Android `EncryptedSharedPreferences` (or Tink-backed DataStore), iOS Keychain via cinterop. New `data-local/secure/` sub-package. |
| iOS background tasks | ❌ none — `AndroidBackgroundSyncScheduler` is Android-only ([`sync/default/src/androidMain/.../AndroidBackgroundSyncScheduler.kt`](../sync/default/src/androidMain/kotlin/com/georgeci/moneysurfer/sync/scheduler/AndroidBackgroundSyncScheduler.kt)) | Periodic refresh on iOS | `BGAppRefreshTask` / `BGTaskScheduler` wrapper. New `sync/default/src/iosMain/.../IosBackgroundSyncScheduler.kt`. |
| Android background work | ✅ WorkManager already wired | Add a periodic `BankRefreshWorker` reusing existing infra | — |
| JSON parsing | ✅ `kotlinx-serialization-json` | DTOs annotated `@Serializable` | — |
| Coroutines / Flow | ✅ in use | — | — |
| `data-remote` banking sub-package | ❌ none — only Firebase code there | New `data-remote/src/commonMain/.../banking/` | Mirrors `data-remote/.../firestore/` structure. |
| Logging redaction | unclear | Don't log tokens, account numbers, descriptions | Add a redaction wrapper around HTTP logging in banking adapters. |

## Secrets handling

- **Tokens never leave the device.** Stored in `SecureStorage` (Keychain / EncryptedSharedPreferences). Never written to Firestore, never logged.
- **`consentId` may sync** across user's own devices — encrypt the field client-side before write. Decision pending: pick AES-GCM key derived from a per-user key from Firebase Auth or a dedicated user-set passphrase. Open question.
- **Provider client_secret** — most aggregators require server-to-server auth (we are the partner, not the user). This forces a question: do we keep `client_secret` on-device (risky — extractable) or stand up a thin proxy? See [Server vs on-device](#server-vs-on-device).
- **`refresh_token`** semantics differ by provider; some bind to one device, some don't. Treat as device-local always.

## Server vs on-device

Open architectural question. Tradeoffs:

| Aspect | On-device (no server) | Thin proxy server |
|---|---|---|
| Provider `client_secret` | Embedded in app — extractable via APK reverse-engineering. Some providers tolerate it; others contractually forbid it. | Held server-side, never shipped. Standard practice. |
| Cost | Zero infra | Adds a service (Cloud Functions / Cloud Run / similar). Not in current stack. |
| Latency | One hop | Two hops |
| AISP-relationship simplicity | Each user is independent | Centralised log of connections (privacy concern + storage cost) |
| MoneySurfer's current infra | Firebase only | Would need to introduce Cloud Functions or equivalent |

**Lean**: stand up a tiny Cloud Functions proxy that **only signs token-exchange requests** and proxies them to the aggregator. It does not see transaction payloads. Transaction fetches go on-device with a short-lived token. Confirm with provider ToS before committing.

## Sync interplay

- Pulled transactions enter the existing outbox → Firestore. Same path Track A uses.
- A user might have entered a transaction manually that the API later pulls again. Use `externalId` dedupe (Track A's strategy) plus a `(account, date, amountMinor, normalisedDescription)` fuzzy match within ±48 h to detect collisions; on collision, prefer the existing manual row and mark the API row as suppressed in the `ImportBatch`.
- `BankConnection` syncs as its own entity type; `RefreshBankConnectionUseCase` is device-local (each device refreshes independently — provider tokens are per-device). Multiple devices refreshing the same connection deduplicate via `externalId`.
- **Read [`docs/architecture/sync.md`](../docs/architecture/sync.md) before extending sync** — required by AGENTS.md.

## Legal / regulatory

- **PSD2 (EU/UK)**: AIS only via licensed aggregator. We are a TPP-client / agent under the aggregator's contract. No own licence needed.
- **GDPR**: bank data is personal + sensitive. Required surfaces: explicit consent screen, "disconnect & delete data" action (`DisconnectBankUseCase` + `RevertImportBatchUseCase`), retention policy, breach process. Local-first storage helps the data-minimisation argument.
- **Russia**: no PSD2 analogue, no public personal API. Path is Salt Edge under their licence basis, or skip Track B for RU and stay on Track A.
- **Provider ToS**: each aggregator has its own data-handling and brand-use rules. Track and version them in `docs/architecture/banking-providers.md` once we pick one.

## Cost projection (rough)

Numbers below are placeholders pending current provider quotes — fill at implementation time.

| Provider | EU/UK | RU | 100 users | 1k users | 10k users |
|---|---|---|---|---|---|
| GoCardless | ✓ | ✗ | €0 | €0 | likely free, confirm |
| TrueLayer | ✓ (UK) | ✗ | $$ | $$$ | $$$$ |
| Tink | ✓ | ✗ | enterprise | enterprise | enterprise |
| Salt Edge | ✓ | ✓ | $$ | $$$ | $$$$ |

GoCardless free tier covers v1 EU/UK pilot at any plausible MoneySurfer scale.

## Recommendation & rollout

| Phase | Scope | Output |
|---|---|---|
| 2.0 | Add Ktor + secure storage + WebView wrapper + iOS background scheduler. No UI yet. | Platform foundation. |
| 2.1 | GoCardless adapter in `data-remote/banking/gocardless/` + `BankConnection` domain + `feature/bank-connect/` UI. EU/UK only. Manual refresh button. | Pilot ships. |
| 2.2 | Periodic refresh — Android `BankRefreshWorker`, iOS `BGAppRefreshTask`. Reauth flow on consent expiry. | Auto-sync lands. |
| 2.3 | Salt Edge adapter for RU **iff** budget + legal approved. Same `BankProvider` contract; same UI shell. | RU coverage. |
| 2.4 | Tighten dedupe, add user-visible "API vs manual" badge, telemetry. | Polish. |

## Status quo (audit)

| Layer | State | Files |
|---|---|---|
| HTTP client | ❌ none | [`gradle/libs.versions.toml`](../gradle/libs.versions.toml), [`data-remote/build.gradle.kts`](../data-remote/build.gradle.kts) |
| WebView (KMP) | ❌ none | — |
| Secure storage | ❌ DataStore unencrypted | [`data-local/.../datastore/DataStoreFactory.kt`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/datastore/) + Android/iOS actuals |
| iOS background scheduler | ❌ none | [`sync/default/src/androidMain/.../AndroidBackgroundSyncScheduler.kt`](../sync/default/src/androidMain/kotlin/com/georgeci/moneysurfer/sync/scheduler/AndroidBackgroundSyncScheduler.kt) (Android-only) |
| `data-remote/banking/` | ❌ none — only Firebase paths | [`data-remote/`](../data-remote/) |
| `BankConnection` domain | ❌ none | — |
| `BankProvider` interface | ❌ none | — |
| Sync entity for connections | ❌ no `BANK_CONNECTION` value | [`sync/.../api/SyncEntityType.kt`](../sync/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/api/SyncEntityType.kt) (path approximate — verify at implementation) |
| Track A foundations (`TransactionSource`, `ImportBatch`, batch insert) | ❌ none — must land in Track A first | see [bank-statement-import.md](bank-statement-import.md) |

## Open questions

- Confirm GoCardless production pricing for AIS at current rules — free tier still in place?
- Stand up a tiny proxy server, or attempt fully on-device with provider blessing?
- Encrypt `consentId` and `BankConnection.institutionName` in Firestore? Where does the encryption key live?
- Refresh frequency: every 6 h, daily, or on app open only? Battery / quota tradeoffs.
- Reauth UX when consent expires mid-session — modal blocker or banner?
- Multiple workspaces: can the same bank connection feed accounts in different workspaces, or pin to one?
- Do we surface bank-side balance ("authoritative") next to MoneySurfer's computed balance, and how to handle drift?
- Localisation of provider-returned descriptions — keep verbatim or run a normaliser?
- Telemetry: log connection success/failure rates without leaking PII — what minimal event schema?
