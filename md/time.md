# Time in MoneySurfer

Целевая модель времени. Источник правды — этот файл и
[docs/architecture/data-models.md](../docs/architecture/data-models.md).

## Короткое правило

| Тип | Когда использовать |
|---|---|
| `kotlin.time.Instant` | Технический момент: создание, изменение, удаление, синхронизация. Хорошо для сортировки, логов, sync/conflict resolution. Также «когда реально произошла бизнес-операция» (`Transaction.operationAt`). |
| `kotlinx.datetime.LocalDate` | Календарная дата без времени: `Budget.startDate`, `RecurringRule.startDate`, период бюджета, фильтры по дню. |
| `kotlinx.datetime.YearMonth` | Месячные периоды: бюджеты, лимиты, отчёты, статистика. |
| `kotlinx.datetime.LocalDateTime` | Только UI/input (date+time picker). Не основное поле в domain/db. |

```kotlin
// Instant       -> когда реально произошло
// LocalDate     -> к какому дню относится
// YearMonth     -> месяц отчёта/лимита
// LocalDateTime -> UI/input, не основной storage
```

`kotlin.time.Instant` каноничен. `kotlinx.datetime.Instant` в новом коде не
использовать.

## Слои

### Domain

Domain-модели объявляют богатые типы:

- `Workspace.createdAt: Instant`, `updatedAt: Instant`
- `WorkspaceMember.createdAt / updatedAt / leftAt? / removedAt?: Instant(?)`
- `WorkspaceInvite.createdAt / updatedAt / expiresAt: Instant`, `respondedAt: Instant?`
- `Category.createdAt / updatedAt: Instant`
- `Account.updatedAt: Instant`
- `Transaction.operationAt: Instant` (момент операции), `createdAt: Instant`,
  `updatedAt: Instant`
- `Budget.startDate: LocalDate`, `createdAt / updatedAt: Instant`
- `RecurringRule.startDate: LocalDate`, `nextRunAt: Instant?`,
  `createdAt / updatedAt: Instant`

«Now»: инжектить `domain.primitives.Clock` и вызывать `clock.now()`.
`kotlin.time.Clock.System.now()` напрямую не вызывать вне этой абстракции.

### Data (Room и Firestore)

Хранение остаётся примитивным:

- `Instant` ↔ `Long epochMillis`
- `LocalDate` ↔ ISO-8601 `String` (`"2026-05-04"`)
- `YearMonth` ↔ ISO-8601 `String` (`"2026-05"`)

Конверсия централизованно в `data-local` и `data-remote` мапперах. Domain
никогда не знает про `Long`/`String` для времени.

```kotlin
internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
internal fun Instant.toEpochMillis(): Long = toEpochMilliseconds()
internal fun String.toLocalDate(): LocalDate = LocalDate.parse(this)
internal fun LocalDate.toIso(): String = toString()
internal fun String.toYearMonth(): YearMonth = YearMonth.parse(this)
internal fun YearMonth.toIso(): String = toString()
```

### Sync / wire

- Cursor — `Instant` в коде, `Long` на проводе.
- Firestore query: `where("updatedAt").greaterThan(cursor.toEpochMilliseconds())`
  + `orderBy("updatedAt")`. LWW сравнивает epoch order.
- Tie-break при равных миллисекундах: текущая стратегия — local wins. Если
  конфликты станут заметны, добавить детерминированный `(updatedAt, deviceId,
  mutationId)` tie-breaker.

## UI и группировка

Никогда не группировать по `epochMillis / dayMs`. Всегда через `TimeZone`:

```kotlin
val zone = TimeZone.currentSystemDefault()
val day: LocalDate = instant.toLocalDateTime(zone).date
```

Список транзакций сортируется `(operationAt → LocalDate via zone) DESC,
createdAt DESC`. `createdAt` — стабильный tiebreaker внутри одного дня.

Date/time picker отдаёт `LocalDateTime`, конвертим в `Instant` с
`zone.toInstant()` перед записью в `operationAt`.

Полезные фиксированные зоны для тестов:

- `TimeZone.UTC`
- `TimeZone.of("Europe/Madrid")`
- `TimeZone.of("America/Los_Angeles")`

## Исключения

- `UserDoc.createdAt: Long` существует на Firestore, но в domain `User` не
  поднимаем — поле остаётся storage-only.

## Ссылки

- [docs/architecture/data-models.md](../docs/architecture/data-models.md) —
  целевые таблицы Domain/Room/Firestore.
- [docs/architecture/persistence.md](../docs/architecture/persistence.md) —
  правила data-слоя.
- kotlinx-datetime API:
  - https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/kotlinx.datetime/-local-date/
  - https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/kotlinx.datetime/-year-month/
  - https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/kotlinx.datetime/-time-zone/
