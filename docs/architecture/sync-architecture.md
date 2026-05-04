# Sync v2 — Architecture

<!-- DOCS:TOC -->
## Contents
- [Sync v2 — Architecture](#sync-v2-architecture)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [Modules and DAG](#modules-and-dag)
- [File layout — :sync](#file-layout-sync)
- [File layout — :sync-surfer/sync](#file-layout-sync-surfersync)
- [DI](#di)
- [Error-handling convention](#error-handling-convention)
- [Reading order for new contributors](#reading-order-for-new-contributors)
<!-- DOCS:END -->

## TL;DR for agents

- `:sync` owns SDK-free interfaces and value types only.
- `:sync-surfer` holds Firestore/Room implementations of those interfaces.
- `:domain` does not depend on `:sync`; auth/workspace use cases live in `:shared`.
- All sync suspend methods return `SyncResult<T> = Either<SyncError, T>` and never throw as control flow.

READ WHEN:
- adding a new sync module dependency
- introducing a new `:sync` interface or `:sync-surfer` implementation
- changing the error-handling convention
- wiring a new Koin binding into the sync graph

<!-- AI:SECTION id=sync-architecture-rules task=sync,architecture,modules,di,errors -->
## Rules

1. `:sync` does **not** depend on `:sync-surfer`. It owns interfaces and value
   types only — no Firestore, no Room, no DataStore.
2. `:domain` does **not** depend on `:sync`. Auth and workspace use cases
   that talk to the coordinator live in `:shared`.
3. `:sync` allows the same baseline as `:domain` — Kotlin stdlib, coroutines,
   `kotlinx.serialization`, `kotlinx.datetime`, `arrow-core`, `kermit`,
   `koin-annotations`. No SDKs.
4. `SyncCoordinatorImpl` itself lives in `:sync` (no SDK dependencies).
5. Use case implementations (`UploadPendingChangesUseCaseImpl`,
   `PullRemoteChangesUseCaseImpl`, the placeholder
   `NoOpRecalculateLocalProjectionsUseCase`) live in `:sync-surfer` because they
   talk to Firestore and Room.
6. Domain interfaces of sync services (`PendingMutationQueue`,
   `SyncMetaRepository`, `ConflictResolver`, `NetworkMonitor`,
   `BackgroundSyncScheduler`, `SyncTelemetry`, `ApplicationScope`) live in
   `:sync`. Implementations are in `:sync-surfer`, with platform-specific
   schedulers in `:sync-surfer/{android,jvm,ios}Main`.
7. `SyncCommand`, `SyncRequest`, `MergedSyncRequest`, `SyncHandleImpl` are
   `internal` to `:sync/coordinator`.
8. `SyncResult<T> = Either<SyncError, T>`. Every suspend method that can fail
   returns `Either`, never throws. `try/catch` is used **only** at the SDK
   boundary, where `Throwable.toSyncError()` translates the throw into a
   `Left`. `CancellationException` and `SyncCancelledException` are rethrown.
<!-- AI:END -->

## Modules and DAG

```
                  ┌─→ :uikit
:androidApp ──→ :composeApp ──→ :shared ──┼─→ :domain ←─┐
                                          └─→ :sync   ──┘
                                                       ↑
                                            :sync-surfer ─┘
```

## File layout — `:sync`

```
:sync/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/
├── api/
│   ├── LastSyncOutcome.kt
│   ├── SyncCancelToken.kt          (SimpleCancelToken, CompositeCancelToken, SyncCancelledException)
│   ├── SyncCollection.kt
│   ├── SyncEntityType.kt
│   ├── SyncError.kt                (sealed; standalone, not nested under any AppError)
│   ├── SyncHandleStatus.kt
│   ├── SyncMode.kt                 (Enqueue, ReplaceQueued)
│   ├── SyncReason.kt
│   ├── SyncRequestId.kt            (value class)
│   ├── SyncScope.kt                (+ toScope, mergeScope)
│   ├── SyncState.kt
│   ├── SyncStep.kt
│   └── SyncSummary.kt              (+ internal SyncSummaryBuilder)
├── coordinator/
│   ├── MergedSyncRequest.kt        (internal)
│   ├── SyncCommand.kt              (internal sealed)
│   ├── SyncCoordinator.kt
│   ├── SyncCoordinatorImpl.kt      (@Single)
│   ├── SyncHandle.kt
│   ├── SyncHandleImpl.kt           (internal)
│   └── SyncRequest.kt              (internal)
├── di/SyncModule.kt                (@Module @ComponentScan)
├── network/
│   ├── NetworkMonitor.kt
│   └── NetworkWaitMode.kt          (+ Set<SyncReason>.toNetworkWaitMode)
├── projection/
│   ├── ProjectionScope.kt
│   └── ProjectionSummary.kt
├── repository/
│   ├── ConflictResolver.kt
│   ├── MutationOperation.kt
│   ├── PendingMutation.kt
│   ├── PendingMutationQueue.kt
│   └── SyncMetaRepository.kt
├── runtime/ApplicationScope.kt     (@Single)
├── scheduler/BackgroundSyncScheduler.kt
├── telemetry/SyncTelemetry.kt      (+ NoOp object)
└── usecase/
    ├── PullRemoteChangesUseCase.kt
    ├── RecalculateLocalProjectionsUseCase.kt
    └── UploadPendingChangesUseCase.kt
```

## File layout — `:sync-surfer/sync`

```
sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/
├── AppConfigRepositoryImpl.kt              (Firestore appConfig/mobile)
├── AppVersionGateImpl.kt                   (status StateFlow + isSyncAllowed)
├── KermitSyncTelemetry.kt                  (@Single binds SyncTelemetry)
├── LwwConflictResolver.kt                  (@Single binds ConflictResolver)
├── NoOpNetworkMonitor.kt                   (always online — Phase 4 placeholder)
├── NoOpRecalculateLocalProjectionsUseCase  (@Single binds use case)
├── OutboxEnqueuer.kt                       (demo + version gate, payload packaging)
├── PendingMutationQueueImpl.kt             (@Single binds queue, on top of DAO)
├── PullRemoteChangesUseCaseImpl.kt
├── SessionShutdownGateImpl.kt              (@Single binds SessionShutdownGate from :domain)
├── SyncDtoMappers.kt                       (entity ↔ DTO conversions)
├── SyncDtos.kt                             (internal serializable DTOs)
├── SyncErrorClassifier.kt                  (Throwable.toSyncError())
├── SyncMetaRepositoryImpl.kt
└── UploadPendingChangesUseCaseImpl.kt

sync-surfer/src/androidMain/kotlin/com/georgeci/moneysurfer/data/sync/
├── AndroidBackgroundSyncScheduler.kt        (@Single binds scheduler — WorkManager)
└── SyncWorker.kt                            (CoroutineWorker + KoinComponent)

sync-surfer/src/iosMain/kotlin/com/georgeci/moneysurfer/data/sync/
└── IosBackgroundSyncScheduler.kt           (@Single — logging stub until BGTaskScheduler is wired)

sync-surfer/src/jvmMain/kotlin/com/georgeci/moneysurfer/data/sync/
└── DesktopBackgroundSyncScheduler.kt       (@Single — coroutine delay loop on ApplicationScope)
```

## DI

`:sync` exposes a single Koin module via `koin-annotations`:

```kotlin
// :sync/di/SyncModule.kt
@Module
@ComponentScan("com.georgeci.moneysurfer.sync")
class SyncModule
```

`:composeApp` already aggregates module classes generated by koin-annotations
across the project. Implementations in `:sync-surfer` advertise themselves with
`@Single(binds = [...])`.

Notable bindings:

| Interface (`:sync` / `:domain`)       | Implementation (`:sync-surfer`)                       |
|----------------------------------------|-------------------------------------------------------|
| `SyncCoordinator`                      | `SyncCoordinatorImpl` (in `:sync`)                    |
| `PendingMutationQueue`                 | `PendingMutationQueueImpl`                            |
| `SyncMetaRepository`                   | `SyncMetaRepositoryImpl`                              |
| `ConflictResolver`                     | `LwwConflictResolver`                                 |
| `NetworkMonitor`                       | `NoOpNetworkMonitor`                                  |
| `UploadPendingChangesUseCase`          | `UploadPendingChangesUseCaseImpl`                     |
| `PullRemoteChangesUseCase`             | `PullRemoteChangesUseCaseImpl`                        |
| `RecalculateLocalProjectionsUseCase`   | `NoOpRecalculateLocalProjectionsUseCase`              |
| `BackgroundSyncScheduler`              | per-platform — Android / Desktop / iOS                |
| `SyncTelemetry`                        | `KermitSyncTelemetry`                                 |
| `AppVersionGate` (from `:domain`)      | `AppVersionGateImpl`                                  |
| `SessionShutdownGate` (from `:domain`) | `SessionShutdownGateImpl`                             |

`OutboxEnqueuer` is a `@Single` plain class injected into every repository
that does dual-writes. It does not bind to an interface — it is a thin
helper that wraps `PendingMutationQueue` with the demo + version gate
checks (see [sync-outbox.md](sync-outbox.md)).

## Error-handling convention

`SyncResult<T> = Either<SyncError, T>`. Every suspend method that can fail
returns `Either`, never throws as control flow.

Pipeline code uses arrow `either { … bind() }`:

- The coordinator's `runSyncRequest` is a single `either { … }` block. Each
  pipeline stage does `bind()`; the first `Left` short-circuits the rest.
- Use case impls are `either { … }` blocks too. Inside them, `try/catch`
  is used **only** at the SDK boundary: catch `Throwable`, call
  `Throwable.toSyncError()` from
  [SyncErrorClassifier.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncErrorClassifier.kt),
  then `raise(...)`.
- `CancellationException` and the wrapper `SyncCancelledException` are
  rethrown — they are not control-flow errors, they are cooperative
  cancellation.

Example from
[SyncCoordinatorImpl.runSyncRequest](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/coordinator/SyncCoordinatorImpl.kt):

```kotlin
return either {
    emitStep(request, SyncStep.Started)
    cancelToken.throwIfCancelled()

    ensureAppVersionAllowed().bind()       // SyncError.UnsupportedAppVersion
    awaitNetwork(request, cancelToken).bind()

    emitStep(request, SyncStep.UploadingPendingChanges)
    val uploadSummary = uploadPendingChangesUseCase(...).bind()
    summary.addUpload(uploadSummary.uploadedCount)
    // …
    summary.build()
}
```

`SyncError` is a sealed interface with an `isRetryable: Boolean` property —
the Android `SyncWorker` and any future retry layer branch on this. The
mapping between Firestore exceptions and `SyncError` lives in
`classifyFirestore` inside
[SyncErrorClassifier.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncErrorClassifier.kt)
and currently inspects message strings (gitlive does not expose typed
`Code` uniformly across platforms).

## Reading order for new contributors

1. [sync-coordinator.md](sync-coordinator.md) — to understand the actor
   loop and the lifecycle of a single `SyncHandle`.
2. [sync-outbox.md](sync-outbox.md) — to understand how a tap on
   "save transaction" results in a Firestore write.
3. [sync-pull-lww.md](sync-pull-lww.md) — to understand how remote
   changes land in Room.
4. [sync-platform.md](sync-platform.md) — to understand what triggers
   sync outside the UI.
