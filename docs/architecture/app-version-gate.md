# App Version Gate

<!-- DOCS:TOC -->
## Contents
- [App Version Gate](#app-version-gate)
- [TL;DR for agents](#tldr-for-agents)
- [Remote config schema](#remote-config-schema)
- [Domain contract](#domain-contract)
- [Enforcement points](#enforcement-points)
- [Firestore rules backstop](#firestore-rules-backstop)
- [Forward-looking notes](#forward-looking-notes)
<!-- DOCS:END -->

## TL;DR for agents

- Old client builds are blocked from writing to Firestore once their `versionCode` falls below the published cutoff.
- Cutoff lives in `appConfig/mobile`; the local snapshot lives in `AppVersionGate.status`.
- Every outgoing Firestore mutation is stamped with `clientVersionCode` so the rules can reject pre-cutoff payloads.
- Both client gate (UX block + write refusal) and rules backstop (data-shape protection) are in place — gate is fail-open on transient fetch failures.

READ WHEN:
- bumping the supported-version floor
- adding a new entity write path (must stamp `clientVersionCode`)
- editing `firestore.rules` `hasValidClientVersion()`
- wiring a new write use case (must consult `AppVersionGate.isSyncAllowed()` via `OutboxEnqueuer`)
- changing app-startup ordering around `CheckAppVersionUseCase`

Related: [persistence](persistence.md), [sync](sync.md), [firestore-rules-bugs](firestore-rules-bugs.md).

<!-- AI:SECTION id=app-version-gate-config task=app-version,firestore,remote-config -->
## Remote config schema

Single document at `appConfig/mobile`:

```text
appConfig/mobile
  minSupportedAppVersionCode: Int
  latestAppVersionCode: Int
  forceUpdate: Boolean
  message: String?
```

Numeric `versionCode` is authoritative — never compare with semver strings. Domain mirror:

```kotlin
data class RemoteAppConfig(
    val minSupportedAppVersionCode: Int,
    val latestAppVersionCode: Int,
    val forceUpdate: Boolean,
    val message: String?,
)
```

Comparison rules (implemented in `AppVersionGateImpl.evaluate`):

```text
versionCode < minSupportedAppVersionCode  → Unsupported (hard block)
forceUpdate == true                       → Unsupported (hard block, regardless of version)
versionCode < latestAppVersionCode        → UpdateAvailable (soft prompt, sync still allowed)
otherwise                                  → Supported
```
<!-- AI:END -->

<!-- AI:SECTION id=app-version-gate-domain task=app-version,domain,kotlin -->
## Domain contract

`AppVersionStatus` (`domain/.../model/AppVersionStatus.kt`):

```kotlin
sealed interface AppVersionStatus {
    data object Supported : AppVersionStatus
    data class UpdateAvailable(val message: String?) : AppVersionStatus
    data class Unsupported(val message: String) : AppVersionStatus
}
```

`AppVersionGate` (`domain/.../repositories/AppVersionGate.kt`):

```kotlin
interface AppVersionGate {
    /** `null` until the first successful refresh. */
    val status: StateFlow<AppVersionStatus?>

    suspend fun refresh(): AppVersionStatus

    /** Convenience read for write-paths — assumes refresh has run. */
    fun isSyncAllowed(): Boolean
}
```

Implementation: `data-remote/.../repository/AppVersionGateImpl.kt`. Notable behaviour:

- Cached snapshot — read paths consult `status.value` synchronously without hitting the network.
- Fail-open: if `AppConfigRepository.fetch()` returns `null` (missing doc / fetch error), the gate resolves to `Supported` rather than locking users out on a transient Firestore hiccup.
- `isSyncAllowed()` returns `true` for `null`, `Supported`, and `UpdateAvailable`; only persisted `Unsupported` blocks writes.

`CheckAppVersionUseCase` is a thin wrapper over `gate.refresh()` — invoke it once at app start (before `BootstrapSessionUseCase` and `BackgroundSyncScheduler.schedulePeriodic`).
<!-- AI:END -->

<!-- AI:SECTION id=app-version-gate-enforcement task=app-version,sync,outbox -->
## Enforcement points

Three layers cooperate so an out-of-date client cannot poison Firestore:

1. **Outbox enqueue gate.** `OutboxEnqueuerImpl.isEnabled()`
   (`sync/default/.../sync/repository/OutboxEnqueuerImpl.kt`) calls
   `appVersionGate.isSyncAllowed()`. If the gate is `Unsupported`, the
   enqueuer drops the mutation silently — no `PendingMutation` row is
   ever written, so the local change cannot be replayed later.

2. **Sync engine.** `SyncVersionGateImpl`
   (`sync-surfer/.../sync/SyncVersionGateImpl.kt`) adapts the domain
   gate to the engine's `SyncVersionGate` so push/pull steps short-circuit
   when status flips to `Unsupported` mid-flight.

3. **Stamped writes.** Every sync plugin stamps the outgoing DTO with the
   live `appInfo.versionCode` before pushing. Find them by greppling for
   `clientVersionCode = appInfo.versionCode`:

   - `WorkspaceSyncPlugin`
   - `WorkspaceMemberSyncPlugin`
   - `WorkspaceInviteSyncPlugin`
   - `AccountSyncPlugin`
   - `CategorySyncPlugin`
   - `TransactionSyncPlugin`

   The DTO carries a default `clientVersionCode = 1` so legacy local rows
   re-pushed without explicit stamping still satisfy the rules backstop;
   plugins overwrite that default at push time.
<!-- AI:END -->

<!-- AI:SECTION id=app-version-gate-rules task=app-version,firestore-rules,security -->
## Firestore rules backstop

Static floor on every entity write. From `firestore.rules`:

```js
function hasValidClientVersion() {
  return request.resource.data.clientVersionCode is int
    && request.resource.data.clientVersionCode >= 1;
}
```

Applied to `create, update` on every workspace subcollection
(`members`, `invites`, `accounts`, `categories`, `transactions`,
`budgets`, `recurringRules`) plus the `workspaces/{wid}` doc itself.
Soft-delete is an `update`, so the gate covers the tombstone path too.

The constant (`>= 1`) is **static** today — it must be bumped by hand
in lockstep with `appConfig/mobile.minSupportedAppVersionCode` when
retiring a build. See [firestore-rules-bugs.md](firestore-rules-bugs.md)
issue #1 for the synchrony hazard and the dynamic-`get()` alternative
that was rejected on cost grounds.

Real force-update enforcement lives client-side in `AppVersionGate`;
the rules check is a defence-in-depth backstop that prevents
pre-cutoff payloads from landing in Firestore even if a tampered
client tries to bypass the UX block.
<!-- AI:END -->

## Forward-looking notes

Startup-coordinator wiring (`AppLaunchViewModel` integration) and the
read-only fallback UX are not yet implemented; promote them into this
file when the entry-point code stabilizes.
