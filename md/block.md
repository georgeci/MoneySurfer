# App-version gate — forward-looking notes

As-built reference (config schema, `AppVersionGate`, enforcement points, Firestore rules) is promoted to [docs/architecture/app-version-gate.md](../docs/architecture/app-version-gate.md).

This file keeps only the aspirational pieces that don't match the current implementation: target startup ordering and the read-only fallback UX.

## Startup ordering (target)

Цель: проверять версию до bootstrap и sync, чтобы старая клиентская сборка не успела запустить outbox / pull до получения статуса.

```text
App startup
  ↓
1. Local DB migration
  ↓
2. Check app version (через AppVersionGate.refresh())
  ↓
3. If Supported / UpdateAvailable:
     - проверить Firebase Auth currentUser
     - выполнить BootstrapSessionUseCase
     - запустить SyncCoordinator.requestSync(APP_START)

4. If Unsupported:
     - показать blocking screen
     - не запускать BootstrapSessionUseCase
     - не запускать SyncCoordinator
```

Сейчас порядок другой: gate проверяется при enqueue (`OutboxEnqueuerImpl.isEnabled()`) и при push через `SyncVersionGateImpl`. Стартовый barrier в `AppLaunchViewModel` пока не введён. Поведение работает (старая версия отклонит sync), но UX подсветки «обновите приложение» неконсистентен — кнопка sync крутится, потом падает.

Когда стабилизируется entry point, эту секцию надо переписать в as-built и переехать в docs.

## Read-only fallback (deferred)

```text
Запрещено при Unsupported:
  - sync (push/pull)
  - create/update/delete локальных данных
  - запуск BootstrapSessionUseCase

Разрешено опционально:
  - открыть read-only local mode (просмотр сохранённых данных)
  - открыть экран обновления приложения
```

MVP: полностью блокирующий экран. Read-only режим — backlog: требует переписать write use cases чтобы они «знали» про gate (сейчас они проверяют через outbox enqueue, что для read-only недостаточно).

Сообщение пользователю:

```text
Эта версия приложения больше не поддерживается.
Обновите приложение, чтобы продолжить работу.
```
