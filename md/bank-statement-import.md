# Bank Statement Import — Research & Design Draft

> Companion doc: [bank-api-integration.md](bank-api-integration.md) — direct bank/aggregator APIs (Phase 2). This document covers the **MVP path**: user manually exports a statement from their bank's web/mobile app and uploads the file into MoneySurfer.

## TL;DR for agents

- **Recommendation**: ship statement import as the MVP for "load transactions from a bank". Cheap, no licensing, no OAuth, works with every RU and EU/UK bank, gives users immediate value.
- **MVP scope**: CSV import with hand-tuned presets for **Тинькофф/Т-Банк, Сбер, Альфа** (RU) and **Revolut, Wise, Monzo** (EU/UK). Ship OFX as a free bonus (single standard parser covers Starling, Raiffeisen, many EU banks).
- **PDF / OCR**: deferred. No KMP-native PDF text extractor exists; the value/cost tradeoff is bad for v1.
- **Blockers in current code** (greenfield — see [Status quo](#status-quo-audit)): no file picker, no CSV parser, no `TransactionSource`/`externalId`/`ImportBatch` in domain, no batch insert use case.
- **Out of scope here**: direct bank/PSD2 API integration → see [bank-api-integration.md](bank-api-integration.md).

## Goals & non-goals

**Goals**

- User picks a file exported from their bank, MoneySurfer previews the rows, the user confirms target account, transactions land in Room and propagate via the existing outbox to Firestore.
- Re-import of the same period must not produce duplicates.
- Users can review and revert an import as a single batch.

**Non-goals (v1)**

- No automatic, scheduled, or background fetching from a bank — that is Track B.
- No payments, no KYC, no balance reconciliation against the bank's authoritative balance.
- No OCR / image-based PDFs.
- No corporate-only formats (CAMT.053, MT940, 1С) in the first release.

## Two tracks compared

Short orientation — full Track B detail in the companion doc.

| Axis | Statement import (this doc) | Direct API / aggregator |
|---|---|---|
| Cost | Zero | €/$ per connection or per call |
| Coverage | Every bank that lets you export anything | EU/UK strong, RU only via SaltEdge for individuals |
| Legal / regulatory | None — user owns the file | PSD2 AISP licence or licensed aggregator required in EU |
| UX friction | User has to export & upload | One-time consent, then automatic |
| Freshness | As often as user re-exports | Daily / on-demand |
| On-device work | Parse + dedupe | HTTP + OAuth + scheduling + secure storage |
| Time to ship | Small | Multi-phase |

## Format landscape

### What each target bank actually exports

RU (personal accounts):

| Bank | Web/app export | Format(s) | Notes |
|---|---|---|---|
| Тинькофф / Т-Банк | Web + mobile | CSV (UTF-8, `;` delimiter), PDF | CSV is the canonical machine-readable export. Columns documented and stable. |
| Сбер | СберБанк Онлайн web | PDF, CSV, 1С (XML) | CSV is more limited than Тинькофф's; 1С is corporate-only. |
| Альфа-Банк | Web | CSV, PDF, Excel | CSV columns vary by account type (debit / credit card). |
| Райффайзен | Web | CSV, OFX | OFX support is a free win. |
| ВТБ | Web | PDF (primary), CSV partial | PDF is dominant — limits MVP coverage. |
| Газпромбанк, Открытие, Росбанк | Web | PDF mostly | Same constraint as ВТБ. |

EU / UK (personal accounts):

| Bank | Export | Format(s) | Notes |
|---|---|---|---|
| Revolut | Web + app | CSV, PDF | CSV well-documented, includes original currency + rate. |
| Wise | Web + app | CSV, PDF | One CSV per balance/currency. |
| N26 | Web | CSV, PDF | Stable column set. |
| Monzo | Web + app | CSV, PDF | CSV is the recommended format. |
| Starling | Web | CSV, OFX | OFX-friendly. |
| Generic PSD2 EU bank | Online banking | CAMT.053 / MT940 (corporate), CSV (retail varies) | Retail formats are bank-specific. |

### Format ranking for MVP

| Format | Verdict | Why |
|---|---|---|
| CSV | **In** — ship in v1 | Universal but per-bank schemas. Need presets + manual column mapper. |
| OFX | **In** — ship in v1.5 | Standard schema, single parser covers many banks. Tiny added cost. |
| QIF | **Skip** | Legacy, mostly superseded by OFX. Add if asked. |
| CAMT.053 / MT940 | **Skip in v1** | Corporate / SWIFT-flavoured XML. Out of personal-finance scope. |
| 1С (XML) | **Skip in v1** | RU corporate. |
| PDF (text-layer) | **Defer** | Possible with `pdfbox-android` / iOS `PDFKit` per platform, but layout-fragile. Revisit after CSV+OFX ship. |
| PDF (scanned) | **Out** | Requires OCR; out of scope. |
| XLS / XLSX | **Defer** | Treat as CSV-via-conversion in v1; user exports CSV instead. |

## Mapping statement rows → `Transaction`

Source columns vary; target shape is fixed. Domain model: [`domain/.../model/Transaction.kt`](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Transaction.kt).

| Domain field | Source (typical) | Notes |
|---|---|---|
| `id: TransactionId` | generated locally | UUID via `Companion.uuid()`. |
| `workspaceId` | from selected account | Active workspace. |
| `accountId` | user-selected at import time | UI step. |
| `money: Money` | "Amount" column, sign-aware | `Money` is `Long` minor units. Need bank-specific decimal/locale parsing (`,` vs `.`). |
| `currencyCode` | "Currency" column or account default | Some banks emit one CSV per currency. |
| `categoryId` | `null` initially, optional auto-categorisation | See open question on auto-rules. |
| `note` | "Description" / "Merchant" / "Назначение" | Trim, drop bank-internal codes. |
| `timestamp: Long` | "Date" / "Operation date" | Parse per-locale. Banks differ between operation date and posted date — pick operation date. |
| `type: TransactionType` | sign of amount | `INCOME` if `> 0`, `EXPENSE` if `< 0`. |
| `status: TransactionStatus` | always `ACTUAL` | Pending entries dropped or surfaced in preview. |
| `externalId: String?` *(new)* | bank's row id if present, else `sha256(account, date, amount, description)` | For dedupe. |
| `importBatchId: ImportBatchId?` *(new)* | from the new `ImportBatch` aggregate | Lets a single button revert. |
| `source: TransactionSource` *(new enum)* | `STATEMENT_IMPORT` | See domain changes. |

### Dedupe strategy

1. If the source row carries a stable bank id — use it as `externalId`.
2. Otherwise compute `externalId = sha256("${accountId}|${date}|${amountMinor}|${normalizedDescription}")`.
3. On import, look up existing rows by `externalId` within `±48h` window; if found, skip.
4. Surface skipped rows in the preview UI ("3 transactions already imported, will skip").

## UX flow

```
[+] Import statement
  → File picker (CSV/OFX) → file in memory
  → Detect format & bank preset
      ↓                       ↓
  Preset matched         No preset
      ↓                       ↓
  Auto column map     Manual column-mapping screen
      ↓                       ↓
  Preview table (rows, sign, currency, dedupe markers)
      ↓
  Pick target account in MoneySurfer
      ↓
  Confirm → Batch insert → ImportBatch record stored
      ↓
  History screen: list of past imports, revert button per batch
```

Component placement (per AGENTS.md module rules):

- New `feature/import/` module (mirrors `feature/transaction/` shape).
- Use cases live in `domain/`. Parsers live in `data-local/parser/` (parsers are pure, safe in `data-local`).
- File picker via `expect`/`actual` — see [Platform gaps](#platform-gaps).

## Domain changes required (textual)

**These changes are shared with Track B** — Track B reuses the same `TransactionSource`, `externalId`, `ImportBatch`, batch-insert use case. Documented here once.

1. **`TransactionSource` enum** in `domain/.../model/`:
   - Values: `MANUAL`, `STATEMENT_IMPORT`, `BANK_API`.
   - Default `MANUAL` for backwards compatibility.
2. **`Transaction` additions**:
   - `source: TransactionSource = MANUAL`
   - `externalId: String? = null`
   - `importBatchId: ImportBatchId? = null`
3. **`Account` additions** (used by both tracks):
   - `externalAccountId: String? = null`
   - `provider: String? = null` — e.g. `"tinkoff-csv"`, `"gocardless:<institution_id>"`.
4. **`ImportBatch` aggregate** (new) in `domain/.../model/ImportBatch.kt`:
   - `id: ImportBatchId`, `workspaceId`, `accountId`, `source: TransactionSource`, `fileName: String?`, `provider: String?`, `importedAt: Long`, `transactionCount: Int`, `status: ImportBatchStatus` (`COMMITTED`, `REVERTED`).
   - `ImportBatchId` value class with `Companion.uuid()`.
5. **Repository** in `domain/.../repositories/ImportBatchRepository.kt`: `getByWorkspaceId`, `insert`, `markReverted`.
6. **Use cases** in `domain/.../usecase/`:
   - `PreviewStatementUseCase` — pure, takes raw bytes + format hint, returns parsed rows + warnings.
   - `MapColumnsUseCase` — for unknown CSV layouts.
   - `CommitImportUseCase` — orchestrates `ImportBatch` + batch `TransactionRepository.insert`.
   - `RevertImportBatchUseCase` — sets batch to `REVERTED` and tombstones associated transactions through the existing sync outbox.
7. **Storage** ([`data-local/.../db/`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/)):
   - Room migration: add columns to `TransactionEntity`, add columns to `AccountEntity`, add new `ImportBatchEntity` + DAO.
   - Bump DB version. Follow the additive-migration pattern used for budgets ([md/budgets.md](budgets.md) `Status quo` table).
8. **Sync** ([`docs/architecture/sync.md`](../docs/architecture/sync.md)):
   - Extend `SyncEntityType` with `IMPORT_BATCH`.
   - Add `BatchDoc` to [`data-*/sync/SyncDtos.kt`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/) and mappers.
   - Existing transaction outbox carries the new optional fields; Firestore schema is additive (defaults on read).
   - **Read [docs/architecture/sync.md](../docs/architecture/sync.md) before touching outbox** — required by AGENTS.md.

## Platform gaps

Greenfield checklist — items required specifically by Track A.

| Capability | Status today | What's needed for Track A |
|---|---|---|
| File picker | ❌ none | KMP file picker. Candidate: [`filekit`](https://github.com/vinceglb/FileKit) (vinceglb) — Compose Multiplatform, MIT, supports Android/iOS/Desktop. Alternative: hand-rolled `expect`/`actual` with `ActivityResultContracts.GetContent` (Android) and `UIDocumentPickerViewController` (iOS). |
| CSV parser | ❌ none | [`kotlin-csv`](https://github.com/jsoizo/kotlin-csv) (jsoizo) — KMP, MIT. Streaming API. |
| OFX parser | ❌ none | OFX 1.x is a SGML/XML hybrid. Hand-write a minimal parser (covers ~30 OFX tags we care about); avoid Java-only `ofx4j`. |
| `kotlinx.io` / charset detection | partial via `kotlinx-serialization` | Need byte-level reads + charset sniffing (Cyrillic CSVs are often Windows-1251). |
| Decimal / date parsing per locale | indirect | Use `kotlinx-datetime` + per-preset format strings. |
| Batch insert in `TransactionRepository` | absent — only single `insert()` | Add `insertAll(transactions)` in [`domain/.../repositories/TransactionRepository.kt`](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/repositories/TransactionRepository.kt) and matching outbox-aware impl in [`data-local/.../repository/TransactionRepositoryImpl.kt`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/TransactionRepositoryImpl.kt). |
| HTTP client, secure storage, WebView, BG tasks | n/a for Track A | Track B concerns — see companion doc. |

## Legal / privacy notes

- **Ownership**: file is generated by the user from their own bank app — no third-party data flow, no PSD2 / AISP requirement.
- **GDPR**: bank transaction data is sensitive. Show a one-time consent dialog before first import describing local storage + Firestore mirroring. Provide an "erase imported data" action via `RevertImportBatchUseCase` plus account-wipe in settings.
- **Firestore mirroring**: imported transactions follow the existing sync path → end up in Firestore as user data. Decide whether `note` / `description` strings need client-side encryption before write — open question.
- **Russia**: legal for the user to upload their own statement; no regulatory issue.

## Recommendation & rollout

| Phase | Scope | Output |
|---|---|---|
| 0 | Domain shared types: `TransactionSource`, `externalId`, `ImportBatch`, repository iface, batch insert | Foundation reused by Track B. |
| 1.0 | CSV pipeline + presets for Тинькофф, Сбер, Альфа, Revolut, Wise, Monzo + UI flow + ImportBatch + revert | Shippable MVP. |
| 1.1 | OFX parser (Starling, Raiffeisen, generic) | Free coverage expansion. |
| 1.2 | Manual column-mapper for unknown CSV layouts | Long-tail coverage. |
| 1.5 | PDF (text layer) for Тинькофф / ВТБ / Sber as a stretch | Optional. |
| 2.x | Track B kicks in — see [bank-api-integration.md](bank-api-integration.md) | Auto-sync. |

## Status quo (audit)

| Layer | State | Files |
|---|---|---|
| `Transaction` domain model | ⚠ no `source`, `externalId`, `importBatchId` | [`domain/.../model/Transaction.kt`](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Transaction.kt) |
| `Account` domain model | ⚠ no `externalAccountId`, `provider` | [`domain/.../model/Account.kt`](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Account.kt) |
| `ImportBatch` aggregate | ❌ missing entirely | — |
| `TransactionRepository.insertAll` | ❌ only single insert | [`domain/.../repositories/TransactionRepository.kt`](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/repositories/TransactionRepository.kt), [`data-local/.../repository/TransactionRepositoryImpl.kt`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/TransactionRepositoryImpl.kt) |
| Room entities | ⚠ no new columns, no `ImportBatchEntity` | [`data-local/.../db/entity/`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/entity/) |
| File picker | ❌ none | — |
| CSV parser dependency | ❌ not in `libs.versions.toml` | [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) |
| OFX parser | ❌ none | — |
| `feature/import/` module | ❌ none | — |
| Sync DTO `BatchDoc` | ❌ none | [`data-local/.../sync/SyncDtos.kt`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/) |

## Open questions

- Auto-categorisation on import: rules engine (regex on description → category) v1, or post-import only?
- Encrypt `note` / `description` in Firestore mirroring? (cross-cuts manual entry too — bigger decision than Track A alone).
- Pending vs posted transactions — show pending with a marker, or drop?
- Multi-currency CSV (Wise) — split into one batch per currency or merge?
- "Re-import" UX when a row's category was edited locally after import — preserve user edit on re-import?
- Should `ImportBatch.fileName` be persisted to Firestore or kept device-local for privacy?
