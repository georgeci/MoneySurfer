# Skill - Sync v2

Use for outbox, pull, conflict resolution, sync coordinator, scheduler, and
sync status UI.

## Required Reading

- [../../docs/architecture/sync.md](../../docs/architecture/sync.md)
- [../../docs/architecture/sync-architecture.md](../../docs/architecture/sync-architecture.md)
- [../../docs/architecture/sync-outbox.md](../../docs/architecture/sync-outbox.md) for write path
- [../../docs/architecture/sync-pull-lww.md](../../docs/architecture/sync-pull-lww.md) for pull/conflict
- [../../docs/architecture/sync-gaps.md](../../docs/architecture/sync-gaps.md) before claiming complete

## Rules

- Room is local truth.
- Firestore replication goes through sync/outbox paths.
- `sync` owns SDK-free contracts and coordinator runtime.
- `data` owns Room/Firestore implementations.
- Preserve cancellation and `lastOutcome` behavior in `SyncCoordinator`.
- Do not introduce Firestore/Room imports into `sync`.

## Validation

- Prefer focused sync/data tests.
- Device integration requires Firebase Emulator Suite and Android device/AVD.
