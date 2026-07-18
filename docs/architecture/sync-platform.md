# Sync v2 — Platform Layer

<!-- DOCS:TOC -->
## Contents
- [Sync v2 — Platform Layer](#sync-v2--platform-layer)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [BackgroundSyncScheduler](#backgroundsyncscheduler)
  - [Android — WorkManager](#android--workmanager)
  - [iOS — placeholder](#ios--placeholder)
  - [Desktop — coroutine loop](#desktop--coroutine-loop)
  - [Important — the periodic loop in AppLaunchViewModel](#important--the-periodic-loop-in-applaunchviewmodel)
- [NetworkMonitor](#networkmonitor)
  - [NetworkWaitMode](#networkwaitmode)
- [SyncTelemetry](#synctelemetry)
- [App-version gate](#app-version-gate)
- [SessionShutdownGate](#sessionshutdowngate)
- [ApplicationScope](#applicationscope)
<!-- DOCS:END -->

## TL;DR for agents

- `BackgroundSyncScheduler` is the executor of "sync sometime later", not the policy.
- WorkManager floors periodic work at 15 min — the 1-minute foreground refresh lives in `AppLaunchViewModel`.
- iOS scheduler is a logging stub until `BGTaskScheduler` is wired.
- `NoOpNetworkMonitor` always reports online today; offline behaviour falls through to Firestore exception classification.
- `SessionShutdownGate.shutdown()` cancels coordinator + scheduler **before** Room is wiped.

READ WHEN:
- adding a new platform scheduler implementation
- wiring a real `NetworkMonitor`
- changing telemetry destinations or the app-version gate
- editing `LogoutUseCase` / shutdown ordering

<!-- AI:SECTION id=sync-platform-rules task=sync,scheduler,network,telemetry,version-gate -->
## Rules

- The scheduler executes; "should we sync now?" is the caller's decision.
- A `SyncWorker.Result.retry()` branch must depend on `SyncError.isRetryable`.
- The app-version gate is enforced **twice**: once in `OutboxEnqueuer.isEnabled()`
  to block accumulating doomed mutations, and once in `runSyncRequest` so a
  pre-existing outbox is held back when the server flips status mid-session.
- `SessionShutdownGate.shutdown()` order: `coordinator.cancelAll()` first,
  then `scheduler.cancelAll()`. Both calls are idempotent.
- `LogoutUseCase` must call `sessionShutdownGate.shutdown()` **before** clearing
  session pointers and **before** `LocalDataResetRepository.clearAll()`.
- The coordinator's `ApplicationScope` must outlive any `ViewModel`.
<!-- AI:END -->

This doc covers the parts of sync that sit between the coordinator and
the device: scheduling, network awareness, telemetry, the app-version
gate, and session shutdown.

## `BackgroundSyncScheduler`

Interface in
[BackgroundSyncScheduler.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/scheduler/BackgroundSyncScheduler.kt):

```kotlin
interface BackgroundSyncScheduler {
    suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints = SyncConstraints())
    suspend fun scheduleOneShot(delay: Duration, constraints: SyncConstraints = SyncConstraints())
    suspend fun cancelAll()
}

data class SyncConstraints(
    val requireUnmeteredNetwork: Boolean = false,
    val requireCharging: Boolean = false,
    val requireBatteryNotLow: Boolean = true,
)
```

The scheduler is the **executor**, not the policy. "Should we sync now?"
is decided by callers (login flow, app start, manual button); the
scheduler only knows how to run a sync sometime later on the platform's
own terms.

### Android — WorkManager

[AndroidBackgroundSyncScheduler.kt](../../sync/default/src/androidMain/kotlin/com/georgeci/moneysurfer/sync/scheduler/AndroidBackgroundSyncScheduler.kt):

- `schedulePeriodic` builds a `PeriodicWorkRequest` with
  `BackoffPolicy.EXPONENTIAL` (30 s baseline) and
  `enqueueUniquePeriodicWork(KEEP)` so duplicate calls are idempotent.
- `scheduleOneShot` builds a `OneTimeWorkRequest` with `setInitialDelay`
  and `enqueueUniqueWork(REPLACE)`.
- `cancelAll` cancels both unique work names.

`SyncConstraints` map onto `Constraints` directly:
`UNMETERED ⇒ NetworkType.UNMETERED`, otherwise `NetworkType.CONNECTED`.

The worker is
[SyncWorker.kt](../../sync/default/src/androidMain/kotlin/com/georgeci/moneysurfer/sync/scheduler/SyncWorker.kt),
a `CoroutineWorker + KoinComponent`:

```kotlin
override suspend fun doWork(): Result {
    val handle = coordinator.requestSync(reason = SyncReason.BACKGROUND)
    val outcome = handle.result.await()
    return outcome.fold(
        ifLeft = { error -> if (error.isRetryable) Result.retry() else Result.failure() },
        ifRight = { Result.success() },
    )
}
```

This is what makes `SyncError.isRetryable` real on Android: retryable
failures (`NetworkUnavailable`, `Unknown`) feed into WorkManager's
exponential backoff; non-retryable failures (`AuthRequired`,
`PermissionDenied`, `UnsupportedAppVersion`, `Cancelled`,
`StorageError`) terminate without a retry.

A dedicated `WorkerFactory` is needed to inject a Koin-managed worker;
that wiring lives outside this doc — search the Android entrypoint /
`MoneySurferApplication.kt` if it ever stops resolving.

### iOS — placeholder

[IosBackgroundSyncScheduler.kt](../../sync/default/src/iosMain/kotlin/com/georgeci/moneysurfer/sync/scheduler/IosBackgroundSyncScheduler.kt)
is currently a Kermit-logging stub:

```kotlin
override suspend fun schedulePeriodic(interval: Duration, constraints: SyncConstraints) {
    log.i { "schedulePeriodic($interval, $constraints) — no-op until BGTaskScheduler is wired" }
}
```

Until `BGTaskScheduler` is wired through cinterop (and a Swift bridge in
`iosApp/`), iOS sync runs only when the app is open via foreground
triggers (`APP_START`, `MANUAL`, `LOCAL_CHANGE`, etc). This is tracked
as a Phase 4 follow-up; design lives in the original sync plan (§4.5,
background layer).

### Desktop — coroutine loop

[DesktopBackgroundSyncScheduler.kt](../../sync/default/src/jvmMain/kotlin/com/georgeci/moneysurfer/sync/scheduler/DesktopBackgroundSyncScheduler.kt):

- Periodic — `appScope.launch { while (isActive) { delay(interval); coordinator.requestSync(BACKGROUND).result.await() } }`.
- One-shot — same pattern with a single `delay`.
- Mutex-guarded so `schedulePeriodic` / `scheduleOneShot` / `cancelAll`
  cannot race on the underlying `Job` lists.

`SyncConstraints` are accepted but ignored — JVM has no native battery /
network signal. The loop dies with the JVM process; survives window
minimisation because it is on `ApplicationScope`, not on any UI scope.

### Important — the periodic loop in `AppLaunchViewModel`

`BackgroundSyncScheduler.schedulePeriodic` is **not** the production
periodic trigger today. WorkManager's minimum periodic interval is 15
minutes, and the product wants a 1-minute foreground refresh. The
in-process loop in
[AppLaunchViewModel.kt](../../navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppLaunchViewModel.kt)
is what drives periodic sync while the user is signed in:

```kotlin
viewModelScope.launch {
    session.currentFirebaseUid.flow.distinctUntilChanged()
        .collectLatest { uid ->
            if (uid.isNullOrEmpty()) return@collectLatest
            while (isActive) {
                delay(PERIODIC_INTERVAL)              // 1.minutes
                syncCoordinator.requestSync(SyncReason.BACKGROUND)
            }
        }
}
```

`collectLatest` cancels the loop the moment the UID flips to null on
logout. The loop lives on `viewModelScope`, so it dies with the
ViewModel — but `SyncCoordinator` itself runs on `ApplicationScope` and
finishes the in-flight sync.

`BackgroundSyncScheduler` is therefore reserved for *truly background*
sync (process not running, OS-driven wakeup) — currently only the
Android implementation is functional, and only if a caller invokes
`schedulePeriodic` explicitly. None of `:shared` does that today.

## `NetworkMonitor`

Interface in
[NetworkMonitor.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/network/NetworkMonitor.kt).
The only implementation right now is
[NoOpNetworkMonitor.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/network/NoOpNetworkMonitor.kt):

```kotlin
@Single(binds = [NetworkMonitor::class])
class NoOpNetworkMonitor : NetworkMonitor {
    override val online: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}
```

It always reports the device as online. Consequence: `awaitNetwork`
never emits `WaitingForNetwork`, never raises `NetworkUnavailable` from
a bounded timeout — it falls straight through. If the device is actually
offline, the Firestore SDK throws and gets classified into
`SyncError.NetworkUnavailable` by
[SyncErrorClassifier.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncErrorClassifier.kt).

Real platform monitors (Android `ConnectivityManager`, iOS
`NWPathMonitor`) are still pending — see
[sync-gaps.md](sync-gaps.md).

### `NetworkWaitMode`

[NetworkWaitMode.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/network/NetworkWaitMode.kt)
maps `SyncReason → wait policy`:

| Reason          | Mode                |
|------------------|---------------------|
| `BACKGROUND`     | `Bounded(5.minutes)` |
| `APP_START`      | `Bounded(30.seconds)` |
| everything else  | `Indefinite` |

`Set<SyncReason>.toNetworkWaitMode()` picks the strictest bounded
timeout, falling back to `Indefinite`. This is what the coordinator
uses when a merged request carries multiple reasons.

The behaviour matters mostly when a real `NetworkMonitor` ships:

- A 5-minute background tick should not park the worker forever waiting
  for radio to come back — bounded.
- A user pressing "sync" with no network should suspend until they
  reconnect — indefinite, cancellable via `handle.cancel()`.

## `SyncTelemetry`

Interface in
[SyncTelemetry.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/telemetry/SyncTelemetry.kt)
with a built-in `NoOp` object. Production binding is
[KermitSyncTelemetry.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/telemetry/KermitSyncTelemetry.kt) —
structured logging with the tag `SyncTelemetry` and the prefix
`[req=…]` so a single sync run can be grepped end-to-end.

| Hook              | Level | Sample line |
|-------------------|-------|-------------|
| `onSyncStarted`   | info  | `[req=abc] started reasons=[MANUAL] scope=AllUserData` |
| `onStepEntered`   | debug | `[req=abc] step=UploadingPendingChanges` |
| `onSyncCompleted` | info  | `[req=abc] completed in 412ms uploaded=3 downloaded=12 conflicts=0 recalculated=0` |
| `onSyncFailed`    | warn  | `[req=abc] failed in 800ms error=NetworkUnavailable retryable=true` |
| `onSyncCancelled` | info  | `[req=abc] cancelled after 100ms` |

The interface is small enough that swapping in a real metrics pipeline
(Crashlytics / OpenTelemetry / etc.) is one DI change. There is no
`@Single` adversary for `NoOp` — the Kermit binding wins.

## App-version gate

Source:
[AppVersionGateImpl.kt](../../data-remote/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/AppVersionGateImpl.kt).
Interface in `:domain`
([AppVersionGate.kt](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/repositories/AppVersionGate.kt)).

```kotlin
override suspend fun refresh(): AppVersionStatus {
    val config = appConfigRepository.fetch()
    val resolved = if (config == null) Supported else evaluate(config)
    _status.value = resolved
    return resolved
}

override fun isSyncAllowed(): Boolean = when (status.value) {
    null, Supported, is UpdateAvailable -> true
    is Unsupported                       -> false
}
```

`evaluate(config)` reads `appConfig/mobile`:

- `versionCode < minSupportedAppVersionCode` or
  `forceUpdate == true` ⇒ `Unsupported(message)`.
- `versionCode < latestAppVersionCode` ⇒ `UpdateAvailable(message)`.
- otherwise ⇒ `Supported`.

The gate has **two enforcement points**:

1. **Inside the coordinator pipeline.** `runSyncRequest` calls
   `appVersionGate.refresh()` after `SyncStep.Started` and before
   `awaitNetwork`. A status flip from `Supported` to `Unsupported` while
   the user is sitting in the app is therefore picked up on the very
   next sync cycle, not just at next app launch — see
   [SyncCoordinatorImpl.ensureAppVersionAllowed](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/coordinator/SyncCoordinatorImpl.kt).
2. **Inside `OutboxEnqueuer.isEnabled()`.** A blocked build cannot pile
   up new mutations. See [sync-outbox.md](sync-outbox.md).

`SyncError.UnsupportedAppVersion(message)` is non-retryable. Repositories
that produce mutations and the coordinator both refuse to do work until
the user upgrades. UI handling is out of scope here; design lives in
[app-version-gate.md](app-version-gate.md).

## `SessionShutdownGate`

Source:
[SessionShutdownGateImpl.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/SessionShutdownGateImpl.kt),
interface in `:domain`.

```kotlin
override suspend fun shutdown() {
    coordinator.cancelAll()
    scheduler.cancelAll()
}
```

Order matters:

1. `coordinator.cancelAll()` first — cancels the running run plus any
   queued runs. Any in-flight Firestore push is abandoned.
2. `scheduler.cancelAll()` — drops Android `WorkManager` schedules and
   the desktop coroutine loop. Otherwise a scheduled sync could fire
   *after* logout, hit `AuthRequired`, and noisy-fail.

Both calls are idempotent.

`LogoutUseCase` calls `sessionShutdownGate.shutdown()` **before** any
session pointers are cleared and **before**
`localDataResetRepository.clearAll()`. See
[LogoutUseCase.kt](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/usecase/LogoutUseCase.kt).
Sync had to be stopped before Room was wiped, otherwise an in-flight
pull could re-populate Room mid-wipe and produce ghost rows.

## `ApplicationScope`

```kotlin
@Single
class ApplicationScope(
    private val delegate: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : CoroutineScope by delegate
```

Process-bound, supervisor-rooted, on `Dispatchers.Default`. Tests inject
a `TestScope` as the delegate so virtual time advances under their
control.

This is what gives the coordinator its life beyond a single `ViewModel`.
See coordinator FAQ §17.
