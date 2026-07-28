# Time in MoneySurfer

Целевая модель времени. Источник правды — этот файл и
[docs/architecture/data-models.md](../docs/architecture/data-models.md).

## Короткое правило

| Тип | Когда использовать |
|---|---|
| `kotlin.time.Instant` | Технический момент: создание, изменение, удаление, синхронизация. Хорошо для сортировки, логов, sync/conflict resolution. Также «когда реально произошла бизнес-операция» (`Transaction.operationAt`). |
| `kotlinx.datetime.LocalDate` | Календарная дата без времени: `Transaction.operationDate`, `Budget.startDate`, `RecurringRule.startDate`, период бюджета, фильтры по дню. |
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
- `Transaction.operationAt: Instant` (момент операции),
  `operationDate: LocalDate` (бизнес-дата, см. ниже), `createdAt: Instant`,
  `updatedAt: Instant`
- `Budget.startDate: LocalDate`, `createdAt / updatedAt: Instant`
- `RecurringRule.startDate: LocalDate`, `nextRunAt: Instant?`,
  `createdAt / updatedAt: Instant`

«Now»: инжектить `domain.primitives.ClockUseCase` и вызывать `clock.now()`.
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

## `operationAt` и `operationDate`

У транзакции две даты, и это осознанно (коммит `9ae012224`, май 2026).

| Поле | Тип | Отвечает на вопрос | Кто читает |
|---|---|---|---|
| `operationAt` | `Instant` | когда операция произошла | сортировка, аудит |
| `operationDate` | `LocalDate` | к какому дню она отнесена | бюджеты, группировка списка, SQL-окна по датам |

Зачем разделили: до этого календарный день выводился на каждом чтении как
`operationAt.toLocalDateTime(zone).date`, и запись в 23:30 перепрыгивала
границу суток при смене таймзоны. Раз окно бюджета — это диапазон дат,
транзакция могла перескочить между бюджетными периодами просто потому, что
телефон оказался в другом часовом поясе.

Поэтому `operationDate` вычисляется **один раз при записи**, в зоне
пользователя, и дальше не пересчитывается. При редактировании исходное
значение сохраняется (`TransactionCreationState.pinnedOperationDate`) — иначе
смена зоны между сохранением и правкой молча сдвинула бы дату.

Правила:

- Календарный день транзакции читать из `operationDate`. Не выводить его из
  `operationAt` — это ровно тот баг, ради которого поле и завели.
- Фильтры по диапазону дат — по `operationDate`. В Room это ISO-строка
  `YYYY-MM-DD`, поэтому строковое сравнение корректно работает как сравнение
  дат и покрыто индексом `(workspaceId, operationDate DESC, ...)`. Таймзонная
  арифметика в SQL невозможна, так что альтернативы нет.
- `createdAt` — это момент создания строки, не момент операции. Их нельзя
  делать синонимами: у backdated-записей это ломает аудит и tiebreaker.

Легаси-хвост: колонку добавили с дефолтом `''`, читатели прикрывались
`resolveLegacyOperationDate` (выводит дату из `operationAt` в **UTC**, чтобы
один документ Firestore дал одинаковую бизнес-дату на всех устройствах). Когда
список начал фильтровать в SQL, выяснилось, что `'' >= '2026-07-01'` ложно и
старые строки исчезали из любого окна — починено миграцией 27→28
(`OperationDateBackfillMigration`), тоже через UTC, чтобы backfill не сдвинул
дату на день.

## UI и группировка

Никогда не группировать по `epochMillis / dayMs`. Всегда через `TimeZone`:

```kotlin
val zone = TimeZone.currentSystemDefault()
val day: LocalDate = instant.toLocalDateTime(zone).date
```

Это правило про `Instant`-поля. Список транзакций группируется по
`operationDate` напрямую, без зонной арифметики, и сортируется
`(operationDate, operationAt, createdAt) DESC`: день группирует, момент
упорядочивает внутри дня, `createdAt` разводит строки с одинаковым
`operationAt` — что регулярно бывает у импорта и backdated-пачек.

Date/time picker отдаёт `LocalDateTime`, конвертим в `Instant` с
`zone.toInstant()` перед записью в `operationAt`; `operationDate` берётся из
той же зоны один раз при сохранении.

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
