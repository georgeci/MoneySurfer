# Time in MoneySurfer

Дата исследования: 2026-04-29.

## Короткий вывод

`kotlinx-datetime` уже подключен:
  
- version catalog: `kotlinx-datetime = "0.7.1"`
- alias: `libs.kotlinx.datetime`
- modules: `domain`, `data`, `shared`, `sync`, `feature/dashboard`, `feature/settings`, `domain-test-fixtures`

Задача теперь не "подключить библиотеку", а выровнять модель времени. Сейчас код смешивает:

- `Long` как epoch milliseconds (`createdAt`, `updatedAt`, `timestamp`, `expiresAt`)
- `kotlin.time.Instant` для sync/workspace/recurring/outbox
- `kotlinx.datetime.LocalDate`, `LocalDateTime`, `TimeZone` для календарной логики и UI
- platform `System.currentTimeMillis()` через `expect/actual currentTimeMillis()`

Рекомендация: оставить storage/wire формат как `Long epochMillis`, но в domain/shared logic постепенно поднять типы до `Instant` / `LocalDate` / `TimeZone`. Это минимизирует миграции Room/Firestore и убирает часть ошибок с timezone.

## Что говорит kotlinx-datetime

Основная идея библиотеки: разделять физический момент времени и локальное гражданское время.

- `kotlin.time.Instant`: конкретный момент, независимый от timezone. Подходит для audit timestamps, sync cursors, `createdAt`, `updatedAt`, outbox.
- `LocalDate`: дата без времени. Подходит для budget period, calendar filters, recurring schedule start date.
- `LocalDateTime`: локальные дата+время без timezone. Подходит для будущих событий, где важен wall-clock time. Для MoneySurfer применять осторожно.
- `TimeZone`: обязательна при переводе `Instant` в дату/время для UI, группировки и period calculations.
- `Clock.System.now()` не монотонные часы. Для измерения duration лучше `TimeSource.Monotonic`, но для timestamps `Clock.System.now()` норм.

Источники:

- kotlinx-datetime README: https://github.com/Kotlin/kotlinx-datetime
- API `Instant`: https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/kotlinx.datetime/-instant/
- API `LocalDateTime`: https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/kotlinx.datetime/-local-date-time/
- API `TimeZone`: https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/kotlinx.datetime/-time-zone/

## Текущее состояние проекта

### Gradle

`gradle/libs.versions.toml` уже содержит:

```toml
kotlinx-datetime = "0.7.1"
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

Common dependencies уже есть в ключевых модулях:

- `domain/build.gradle.kts`
- `data/build.gradle.kts`
- `shared/build.gradle.kts`
- `sync/build.gradle.kts`

### Domain

Уже использует `kotlinx.datetime`:

- `Budget.startDate: LocalDate`
- `PeriodTotals` принимает `LocalDate` + `TimeZone`
- `RecurringRule.startDate: LocalDate`

Но много бизнес-времени еще как `Long`:

- `Transaction.timestamp`
- `Category.createdAt`
- `WorkspaceInvite.createdAt/updatedAt/expiresAt/respondedAt`
- `WorkspaceMember.createdAt/updatedAt/*At`
- `GetCurrentTimeUseCase(): Long`

### Sync/Data

Sync уже ближе к правильной модели:

- `SyncMetaRepository` cursor/attempt/success as `Instant`
- `PendingMutation.createdAt: Instant`
- `ConflictMetadata.localUpdatedAt/remoteUpdatedAt: Instant?`

Data хранит в Room/Firestore как `Long`. Это нормально для persistence boundary, но конвертеры должны быть явными и централизованными.

### Shared/UI

UI уже конвертирует timestamp в дату:

- transaction creation date picker uses `Instant.fromEpochMilliseconds(...).toLocalDateTime(TimeZone.currentSystemDefault())`
- transaction list groups by `timestamp / dayMs`, что неверно для timezone/DST

## Целевая модель

### Domain model

Использовать:

- `Instant` для моментов:
  - `createdAt`
  - `updatedAt`
  - `expiresAt`
  - `respondedAt`
  - `removedAt`
  - `leftAt`
  - sync cursors
  - outbox creation time
- `LocalDate` для дат без времени:
  - budget start date
  - budget period boundaries
  - transaction calendar date, если UX выбирает только дату
- `TimeZone` как явный параметр use case / formatter, если результат зависит от пользовательской зоны.
- `Duration` из `kotlin.time` оставить для intervals/retry/backoff/scheduler.

### Persistence/wire

Хранить:

- Room: `Long epochMillis`
- Firestore: `Long epochMillis` сейчас оставить, чтобы не ломать rules/query/sync
- DTO: `Long epochMillis`

Конвертировать только на data boundary:

```kotlin
private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
private fun Instant.toEpochMillis(): Long = toEpochMilliseconds()
```

Лучше сделать shared mapper helpers в `data`, не в `domain`, чтобы domain не знал про storage details.

## План подключения и миграции

### 1. Зафиксировать dependency policy

Статус: уже сделано.

Проверить и оставить:

- `libs.kotlinx.datetime` только через version catalog
- no inline dependency coordinates
- commonMain dependency в модулях, где есть `LocalDate`, `TimeZone`, date formatting

Новая зависимость не нужна.

### 2. Заменить `currentTimeMillis()` abstraction

Сейчас:

- `domain/primitives/CurrentTime.kt`: `expect fun currentTimeMillis(): Long`
- actual uses `System.currentTimeMillis()` / iOS NSDate
- `GetCurrentTimeUseCase(): Long`

План:

```kotlin
// domain/primitives/CurrentTime.kt
expect fun currentInstant(): Instant

fun currentTimeMillis(): Long = currentInstant().toEpochMilliseconds()
```

Then:

- keep `currentTimeMillis()` temporarily for compatibility
- change `GetCurrentTimeUseCase` to return `Instant` only after model fields migrate
- avoid new direct `Clock.System.now()` in shared/data except low-level boundary code

Reason: one clock abstraction, easier tests, no platform time calls scattered.

### 3. Fix timezone grouping bug first

`TransactionsByAccountViewModel.dateKey(timestamp)` uses `timestamp / dayMs`. This groups by UTC day, not user local day. Around timezone boundaries and DST, transactions can land under wrong date.

Plan:

```kotlin
private fun dateKey(timestamp: Long, timeZone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(timeZone).date
```

Then compare/group by `LocalDate`.

This is highest-value behavioral fix before broad type migration.

### 4. Domain timestamps migration, one aggregate at a time

Order:

1. `Workspace.createdAt` already `Instant`. Use as reference.
2. `WorkspaceInvite`: migrate `createdAt`, `updatedAt`, `expiresAt`, `respondedAt` to `Instant`.
3. `WorkspaceMember`: migrate `createdAt`, `updatedAt`, `removedAt`, `leftAt` to `Instant`.
4. `Category`: migrate `createdAt`, `updatedAt`.
5. `Transaction`: decide semantics first:
   - if transaction date is "calendar day chosen by user", model should likely be `LocalDate`
   - if exact event timestamp matters, keep `Instant`
   - current UI date picker suggests `LocalDate` may be better long-term

Each step:

- update domain model
- update use cases
- update data mappers
- keep Room/Firestore fields as `Long`
- update tests/fixtures

### 5. Sync contract

Keep sync cursors and conflict timestamps as `Instant`.

Firestore query stays:

```kotlin
where { "updatedAt" greaterThan cursor.toEpochMilliseconds() }
orderBy("updatedAt")
```

Reason: current remote schema uses numeric millis and LWW compares epoch order.

Risk: multiple writes inside same millisecond can tie. Current resolver says equal timestamps -> local wins. If conflicts become common, add deterministic tie-breaker later (`updatedAt`, `deviceId`, `mutationId`). Not part of kotlinx migration.

### 6. Tests

Add tests for:

- transaction grouping in non-UTC timezone
- DST boundary date grouping
- invite expiration using `Instant` math
- data mapper round-trip `Instant <-> Long`
- sync cursor round-trip through Room

Useful fixed zones:

- `TimeZone.UTC`
- `TimeZone.of("Europe/Madrid")`
- `TimeZone.of("America/Los_Angeles")`

### 7. Formatting/UI rules

Never format/group dates by raw millis division.

Use:

```kotlin
val zone = TimeZone.currentSystemDefault()
val localDate = instant.toLocalDateTime(zone).date
```

For future planned local events:

- store `LocalDate` or `LocalDateTime` plus timezone id, not precomputed `Instant`, if user expects same wall-clock date/time after timezone rule changes.

For MoneySurfer now:

- budget periods: `LocalDate`
- transaction list grouping: `LocalDate` derived from timestamp and zone
- sync/audit timestamps: `Instant`

## Proposed implementation phases

### Phase 0: Documentation only

This file. No code changes.

### Phase 1: Safe fixes

- Add `currentInstant()` abstraction while keeping `currentTimeMillis()`.
- Replace direct shared/data `Clock.System.now().toEpochMilliseconds()` with `currentTimeMillis()` or injected clock where module rules allow.
- Fix transaction grouping with `TimeZone`.

### Phase 2: Domain cleanup

- Migrate invite/member/category timestamps from `Long` to `Instant`.
- Keep mappers converting to/from Room/Firestore `Long`.
- Update tests and fixtures.

### Phase 3: Transaction date decision

Pick one:

- `Instant`: transaction is exact moment.
- `LocalDate`: transaction is user-entered financial date.

Recommendation: `LocalDate` for financial transaction date, plus optional `createdAt/updatedAt: Instant` for audit/sync. Existing `timestamp` can remain remote/storage millis during migration, but domain should expose calendar semantics.

## Acceptance criteria

- `./gradlew :domain:jvmTest :data:jvmTest :shared:jvmTest :sync:jvmTest` passes.
- No new bare `System.currentTimeMillis()` outside platform actual implementations.
- No new raw `timestamp / dayMs` date grouping.
- Domain model uses `Instant` or `LocalDate` where semantics are clear.
- Room/Firestore schemas do not need migration unless field names/types change.
