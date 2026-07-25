# ADR-004 Configuration and Feature Flags

<!-- DOCS:TOC -->
## Contents
- [ADR-004 Configuration and Feature Flags](#adr-004-configuration-and-feature-flags)
- [TL;DR for agents](#tldr-for-agents)
- [Current state](#current-state)
- [Decision](#decision)
- [Why](#why)
- [Keys](#keys)
- [Layers and precedence](#layers-and-precedence)
- [Version gate stays as it is](#version-gate-stays-as-it-is)
- [Per-user sync through the outbox](#per-user-sync-through-the-outbox)
- [Per-user entitlements (future)](#per-user-entitlements-future)
- [Session lifecycle](#session-lifecycle)
- [Session pointers stay out](#session-pointers-stay-out)
- [Module boundaries](#module-boundaries)
- [Dependency injection](#dependency-injection)
- [Debug overrides](#debug-overrides)
- [Write volume](#write-volume)
- [Rules](#rules)
- [Migration](#migration)
- [Alternatives considered](#alternatives-considered)
- [Consequences](#consequences)
<!-- DOCS:END -->

## TL;DR for agents

- One key-value engine (`Config`) resolves every configuration value from ordered layers.
- Features never see `Config`. They inject typed domain facades: `UiPreferences`,
  `SyncSettings`, `HostCapabilities`, `AppVersionGate`, `DebugConfigInspector`.
- The engine lives in `app-config/api` + `app-config/default`, which `feature/*` must not
  depend on.
- Precedence: `Debug > Local > RemoteGlobal > Build > key default`.
- Per-user settings are not a read layer — they are synced into `Local` through the outbox.

READ WHEN:

- adding a feature flag or a user setting
- wiring host-specific behaviour (online vs offline build)
- changing remote config schema or the app-version gate
- touching `UiPreferences`, `SyncFeatureFlag`, `OfflineBuildFlags`, `*FeatureConfig`

## Current state

Four unrelated mechanisms answer the question "what is this value?".

| Mechanism | Where | Shape | Runtime changeable |
| --- | --- | --- | --- |
| Host flags | `OfflineBuildFlags`, `SyncFeatureFlag`, `SignInFeatureConfig`, `TransactionCreationFeatureConfig` | Koin-bound data classes | no — rebuild + release |
| Local preferences | `PreferenceStore` → `UiSettingsDataSource` → `PrefAdapters` → `UiPreferences` | DataStore, `Pref<T>` | yes, device only |
| Remote global | `AppConfigRepository` → `appConfig/mobile` | one-shot typed struct, no cache, no flow | yes, but only the version gate reads it |
| Per-user remote | — | absent | — |

Costs of the split: a new flag means a new class plus a binding in each host plus two
mirror tests; the remote struct cannot grow without a DTO change and an offline no-op;
nothing survives a cold start offline; there is no precedence model at all.

`FirebaseConfig` (emulator toggle) is deliberately out of scope — it is read before
Firestore exists and cannot depend on a Firestore-backed layer.

<!-- AI:SECTION id=adr-configuration task=adr,config,flags,preferences -->

## Decision

Introduce a single key-value configuration engine with ordered layers, and expose it to
the rest of the app only through typed domain facades.

```text
DataStore ─┐
Firestore ─┼─> ConfigSource (per layer) ─> Config (LayeredConfig) ─> domain facades ─> features
Build map ─┘
```

`Config` is infrastructure, not API. It replaces `UiSettingsDataSource` and `PrefAdapters`
rather than stacking on top of them — the read chain gets one hop shorter than it is today.
`PreferenceStore` survives as the backing of the session store (see
[Session pointers stay out](#session-pointers-stay-out)).

## Why

- One resolution model instead of four, with explicit precedence.
- Server-side kill switches without a release.
- Adding a flag is one line in a key object; a server-owned flag is zero client lines.
- Cold-start and offline reads stay correct because every layer is mirrored locally.
- Host wiring (`online` vs `offline`) stays host-owned, so it cannot regress through Koin
  module load order — the property the current per-host bindings were built to protect.

## Keys

Keys are compile-time constants, not DI participants. The number of Koin bindings does not
grow with the number of flags.

```kotlin
open class ConfigKey<T : Any>(
    val name: String,
    val default: T,
    internal val codec: ConfigCodec<T>,
    /** Opt-in: only these keys are served by the RemoteGlobal layer. */
    val remoteOverridable: Boolean = false,
)

/** Writable subtype. Only settings get a setter; server flags cannot be written by mistake. */
class SettingKey<T : Any>(name: String, default: T, codec: ConfigCodec<T>, val sync: Boolean)
    : ConfigKey<T>(name, default, codec, remoteOverridable = false)

interface ConfigCodec<T : Any> {
    fun encode(value: T): String
    /** `null` = undecodable; the layer is then treated as not holding this key. */
    fun decode(raw: String): T?
}
```

A string is the common denominator of every backing store — DataStore preference, the
`config_entry.value` column, a Firestore field — so codecs encode to strings and only the
in-memory Build layer holds typed values directly.

`T : Any`: keys are non-null. "Empty" is a sentinel inside the codec, never a null value,
because the engine already spends `null` on "absent in this layer".

A decode failure is logged and treated as **absent in that layer**, so resolution continues
downwards. This differs from today's `getOrDefault(default)`, but for the migrated keys the
result is identical (no lower layer serves a `SettingKey`), and for a server flag falling
through to the host's Build value is truer to intent than jumping past it to `key.default`.

`Config.handle(key: SettingKey<T>): Pref<T>` accepts only `SettingKey`, so writing to a
server-owned flag fails to compile. `Config.observe(key: ConfigKey<T>)` accepts any key.

Remote reach is opt-in per key, mirroring how the subtype gates writes. `SettingKey`
hard-codes `remoteOverridable = false` and its factories do not expose the parameter, so a
user setting cannot become server-controlled by oversight. See
[Layers and precedence](#layers-and-precedence) for what this protects.

`sync = true` means the value is replicated per user; `sync = false` keeps it device-scoped.
Device-scoped is mandatory for `onboardingCompleted` — replicating it would replay onboarding
across devices. `hasUsedDemo` and the session pointers are not config keys at all; they live in
the session store (see [Session pointers stay out](#session-pointers-stay-out)), which is also
what keeps demo-data isolation intact (`md/sync.md` §2.11).

Key objects are `internal` to the module that owns the matching facade implementation, so no
feature can reach a key directly. The exception is host keys, which the hosts themselves must
enumerate and which are therefore public in `api` — features still cannot see them, because
they have no `app-config` dependency.

```kotlin
// data-local, next to UiPreferencesImpl
internal object UiConfigKeys {
    val themeMode = SettingKey.enum("ui.theme_mode", ThemeMode.System, sync = true)
    val paletteSource = SettingKey.custom("ui.palette_source", PaletteSource.Brand, PaletteSourceCodec, sync = true)
    val onboardingCompleted = SettingKey.bool("ui.onboarding_completed", false, sync = false)
    // ...
    val all: List<ConfigKey<*>> = listOf(themeMode, paletteSource, onboardingCompleted)
}
```

Name, default and codec now live together. The hand-rolled
`runCatching { Enum.valueOf(stored) }.getOrDefault(default)` blocks currently repeated per
field in `UiPreferencesImpl` collapse into the enum codec.

A synced value can be unrepresentable on another platform: `PaletteSource.Dynamic` needs
Material You, and `isDynamicColorAvailable` is `false` off Android. Do **not** solve this by
excluding the key from sync — presets are cross-platform and are the common case. Instead the
facade exposes the stored value and the renderable value separately:

```kotlin
override val paletteSource: Pref<PaletteSource> = config.handle(UiConfigKeys.paletteSource)

override val effectivePaletteSource: Flow<PaletteSource> = paletteSource.flow.map { stored ->
    if (stored is Dynamic && !isDynamicColorAvailable) Brand else stored
}
```

Theming reads `effectivePaletteSource`; the picker binds to the stored `Pref` and simply omits
the Dynamic option where it is unavailable — nothing is highlighted, and no write happens
unless the user actually chooses something. Clamping the `Pref` itself would be worse than the
problem: on a desktop the picker would render the clamped `Brand` as the current selection, and
the first tap would write `Brand` over the `Dynamic` the user set on their phone.

Clamping never belongs in a layer either — a layer that rewrites values would make `resolve()`
lie to the debug panel.

Because keys are `internal`, two consumers that need every key — the debug panel and the
remote-config pull — cannot see the symbols. They read a runtime registry instead, with
each owning module contributing its group:

`getAll<ConfigKeyGroup>()` is acceptable here — group order is irrelevant. The binding shape
matters, though; see [Dependency injection](#dependency-injection). A test asserts that key
names are globally unique; because keys are `internal`, it must run per host against the
assembled Koin graph, not inside `app-config`.

## Layers and precedence

```text
Debug  >  Local  >  RemoteGlobal  >  Build  >  key.default
```

| Layer | Type | Backing | Writable |
| --- | --- | --- | --- |
| Debug | `DebugConfigSource` | separate DataStore file, debug builds only | yes (QA panel) |
| Local | `LocalConfigSource` | Room `config_entry` for `sync = true`, DataStore otherwise; target of remote-user pull | yes |
| RemoteGlobal | `RemoteGlobalConfigSource` | `appConfig/flags` map, fetched on launch/foreground, mirrored to DataStore | no |
| Build | `BuildConfigSource` | in-memory map declared by the host | no |

`null` from a layer means "absent here", not `false`. Resolution takes the first non-null.

**RemoteGlobal serves only keys with `remoteOverridable = true`** — for every other key the
layer returns `null` and resolution falls through to Build. Without that restriction a single
ordered chain plus a free-form remote map lets the server override anything it names:

- `host.is_offline: true` would flip the *online* build onto the offline start-route branch
  while its DI graph stays fully online, destroying the "host wiring cannot regress" property
  this design exists to protect;
- any `SettingKey` the user never wrote has an absent Local layer, so `ui.theme_mode: Dark`
  would silently retheme those devices, and `ui.onboarding_completed: true` would skip
  onboarding on every fresh install ([AppLaunchViewModel](../../navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppLaunchViewModel.kt) gates all startup routing
  on that single read);
- it would also bypass the device-scoping mandate above: `onboardingCompleted` is marked
  `sync = false` precisely so it never replicates, yet a global remote value would reach every
  device anyway.

The opt-in flag restores the non-overridable guarantee that host-owned and user-owned keys
had before the three-policy model collapsed into one chain. The pull/mirror side honours the
same registry check: a key name in `appConfig/mobile` that is unknown or not
`remoteOverridable` is ignored and logged, never mirrored.

A kill switch must not *replace* a user toggle, it must *zero* it. Boolean composition stays
out of the KV layer — keep two keys and combine them in a use case:

The composition lives inside the facade, not in a separate use case: a use case injecting
`Config` would have nowhere legal to live (features cannot see `app-config`, and `domain`
cannot depend on it) and would violate the rule that only facade implementations receive
`Config`.

```kotlin
// data-local, alongside SyncConfigKeys
@Single(binds = [SyncSettings::class])
internal class SyncSettingsImpl(private val config: Config) : SyncSettings {
    override val isEnabled: Flow<Boolean> = combine(
        config.observe(SyncConfigKeys.buildEnabled),   // host: false in both builds today
        config.observe(SyncConfigKeys.remoteEnabled),  // kill switch: default true = "not killed"
        config.observe(SyncConfigKeys.userEnabled),    // SettingKey, default true
    ) { build, remote, user -> build && remote && user }
}
```

Three terms, not two: today's `SyncFeatureFlag` is deliberately `false` in *both* hosts
because the feature is not shipped, and a `remote && user` pair has no slot for that
build-owned term — dropping it would silently enable sync at migration step 1.

**What `true → false` does.** The flag is a flow, so a server kill switch can retract
mid-session and the transition needs a defined shape. The policy is *start nothing new, finish
what is running*:

| | On `isEnabled` becoming `false` |
| --- | --- |
| Periodic ticker | stops — the uid flow is unsubscribed |
| New sync requests (manual, use-case-driven) | no-op before reaching the coordinator |
| Queued requests | left to the coordinator's own queue semantics |
| In-flight sync | **not** cancelled — a half-applied pull is worse than a late one |
| Manual sync UI | hidden; the route stays registered and the screen re-checks defensively |
| Outbox `enqueue` | unaffected — local writes keep queueing |

`enqueue` is deliberately outside the gate: `OutboxEnqueuerImpl` already no-ops without a
Firebase uid, and the sign-in reconciliation in [Session lifecycle](#session-lifecycle) is what
replays writes made while sync was unavailable. Gating enqueue too would drop those writes
instead of deferring them.

**Hydration.** Every backing store is suspend-only, so a synchronous `snapshot()` needs an
in-memory map per source, warmed once. `Config.hydrate()` is a suspend call awaited by
`AppLaunchViewModel` alongside the startup work it already performs, before it resolves the
start route — so nothing renders against a half-loaded chain. Called earlier, `snapshot()`
resolves Build and defaults only, and throws in debug builds so the mistake is not silently
shipped. Mirroring remote layers into DataStore is what makes them survive a cold start
offline; it is not what makes reads synchronous.

## Version gate stays as it is

The app-version gate keeps `AppConfigRepository`, the typed `appConfig/mobile` document and
its awaitable, fail-open `refresh()`. The engine carries new server flags only, in a separate
document. Three reasons, in order of weight:

- **Schema cutover would strand every shipped build.** Released clients parse
  `appConfig/mobile` through `AppConfigDoc`, whose fields all default to `0/0/false/null`
  under a fail-open fetch. Rewriting that document into a flag map makes every pre-cutover
  build resolve `Supported` forever — the force-update mechanism breaks for exactly the
  audience it exists to reach.
- **Fail-open would invert into fail-stale.** Today it is per-launch: fetch fails or the doc
  is missing → `Supported`. Behind a persistent local mirror, a `forceUpdate = true` the
  server later retracts stays mirrored on a device that went offline in between, and every
  subsequent offline launch resolves `Unsupported` from stale local state. The gate also
  needs "fetch now and await a definitive answer", which the engine's
  `observe`/`snapshot`/`handle` surface does not express.
- **No propagation requirement justifies a listener.** An always-on snapshot listener would
  be the first persistent Firestore listener in the codebase, exercising gitlive realtime
  flows on the Desktop JVM target for the first time on the most safety-critical path, for a
  document that changes when a build is retired.

Cost: two remote-config readers coexist. Accepted — the goal is one engine for *flags*, not
one Firestore reader. Unification stays possible later if the engine ever grows an awaitable
`refreshRemote()` with an explicit success/failure branch.

## Per-user sync through the outbox

Per-user configuration is **not** a fourth read layer. It is a transport over `Local`,
matching how every other entity in the app is synced:

- reads always hit `Local` — synchronous, offline-safe;
- pull writes the remote value into `Local`;
- a local write updates `Local` optimistically, then enqueues through `OutboxEnqueuer`.

**One key is one entity.** Outbox rows carry no payload — `SyncEntityPlugin.push` receives
`entityType` / `entityId` / `scopeKey` and re-reads current local state at push time. So
there is no field-merge concept to support, and the natural mapping is:

| | |
| --- | --- |
| `entityType` | `SyncEntityTypes.USER_CONFIG` |
| `entityId` | the key name, e.g. `ui.theme_mode` |
| `scopeKey` | `null` — user-scoped, not workspace-scoped |
| Firestore path | `users/{uid}/config/{keyName}` |

Per-key LWW then falls out of the existing `LwwConflictResolver` with no new primitives:
two devices changing different settings touch different documents, and the same setting
resolves by `updatedAt`. A single map document (`{ key: { value, updatedAt } }`) would have
needed field-level merge that the current pipeline cannot express.

Config must not live as a field on `users/{uid}`: document-level LWW there would clobber the
concurrent `invitedWorkspaceIds` append that `firestore.rules` grants to invite senders.

Two pieces of real work follow:

- **Synced keys live in Room, device-scoped keys stay in DataStore.** Add a
  `config_entry(key, value, updatedAt)` table for `sync = true` keys; `LocalConfigSource`
  routes by `SettingKey.sync`. The reasons are that the push plugin needs `value + updatedAt`
  readable by key at push time, that LWW needs a natural `updatedAt` column, and — most
  usefully — that the storage boundary then coincides with the reset boundary (see
  [Session lifecycle](#session-lifecycle)).

  It is **not** transactional atomicity: the outbox lives in `SyncDatabase`
  (`moneysurfer_sync.db`), a different database from `MoneySurferDatabase`, so no table can
  share a transaction with it and today's dual-writes are two sequential calls. The
  `PendingMutationQueue` KDoc claiming a shared transaction is inaccurate and should be fixed
  in the same change. The crash window it describes is instead closed by the sign-in
  reconciliation below.
- **Pull needs a user-scoped phase, and plugins need a scope discriminator.**
  `PullRemoteChangesUseCaseImpl` iterates workspaces only (phase 1 per-workspace collections,
  phase 2 invites), and it runs *every* registered plugin for *every* workspace. A config
  plugin dragged into phase 1 breaks either way: with `firestoreCollectionName = null` it
  receives the workspace root document in `applyDoc` and a lenient DTO writes a garbage
  `config_entry` row, and with a name it queries a nonexistent
  `workspaces/{wid}/config` path once per workspace. So:

  ```kotlin
  enum class PullScope { Workspace, User }
  interface SyncEntityPlugin {
      val pullScope: PullScope get() = PullScope.Workspace
  }
  ```

  Phase 1 filters to `Workspace`; a new phase 3 runs `User` plugins with `scopeKey = uid`.

**Document schema** — `users/{uid}/config/{keyName}`, three fields, no nesting:

```
{ value: string, updatedAt: int (epoch millis), clientVersionCode: int }
```

`value` is always a string because that is what the codec emits, which keeps write-shape
validation trivial. `updatedAt` is client-clock millis matching `config_entry.updatedAt`, so
LWW compares like with like — the same basis the rest of sync already uses.

**Phase 3 reads the whole collection, without a cursor.** The synced set is about ten tiny
documents and is not expected to grow much, so incremental reads are not worth the cursor
bookkeeping in a new scope — and the rules then only need to permit a bare `list` rather than
prove a filtered, ordered query.

`NoOpOutboxEnqueuer` already exists, so the offline build needs no extra wiring: writes
simply never leave the device.

## Per-user entitlements (future)

Per-user *feature grants* — "receipt upload is on for these accounts" — look like per-user
config but invert the trust model: settings are client-authored, entitlements are
server-authored and the client must never write them. They are therefore **not** a
`SettingKey` and must never share the self-writable `users/{uid}/config` documents, where a
user could simply grant themselves the feature — the client talks to Firestore directly and
there is no server in between.

Not to be confused with `WorkspaceRole`, which already exists, is a synced entity, and is
enforced in `firestore.rules` via `isMember()` / `isOwner()`.

When this is built, the source of truth should be a **custom auth claim**, not a document:

- Enforcement works in both services — `firestore.rules` and `storage.rules` can read
  `request.auth.token.<claim>`, while Storage rules cannot read Firestore at all, so a
  document-based grant is unenforceable for uploads. The token-claim pattern is already used
  in this repo (`request.auth.token.email`).
- No backend is required: claims are set with the Admin SDK from a local script under a
  service account, which is enough for a hand-managed allowlist.
- Nothing new on the client: the claim arrives with the ID token — no document, no pull
  phase, no outbox, no mirror — and offline works because the SDK keeps the last token.
- It cannot be forged; the token is signed.

In engine terms this is a read-only `AuthClaimsConfigSource` serving only `EntitlementKey`s,
with Debug above it so QA can enable a feature locally. Defaults are fail-closed (no grant),
the opposite pole from the version gate's fail-open.

A client flag only ever controls UI visibility; anything protecting data is enforced in rules.

Before committing: verify gitlive `getIdTokenResult(forceRefresh)` exposes claims on all
three targets (unused in the codebase today), and account for propagation delay (a new claim
lands on the next token refresh, up to an hour, or on an explicit refresh) and the 1000-byte
claim budget. A `users/{uid}/entitlements` document plus a billing webhook only becomes
necessary if grants stop being hand-managed — and that change would not touch the client.

## Session lifecycle

**Synced settings are account data and are wiped when the account goes away.** `config_entry`
joins the DAO fan-out in `LocalDataResetRepositoryImpl.clearAll()`, so logout, demo wipe and
account deletion reset theme, palette, container style and period mode. Without this, the next
user on the device inherits the previous one's values *and* wins every LWW comparison against
their own remote documents (local `updatedAt` is newer), so their real settings would never
be pulled and never be pushed.

**Device-scoped state must survive that wipe**, which is exactly why none of it lives in
`config_entry`:

- `hasUsedDemo` — in the session store, not the engine — is the sticky flag `DemoLoginUseCase`
  sets and `WipeDemoDataUseCase` clears; the auth flow reads it on real sign-in to purge orphan
  demo data before bootstrap. Resetting it would let demo data reach Firestore, which
  `md/sync.md` §2.11 forbids. `clearSession()` must not touch it.
- `ui.onboarding_completed` is a `sync = false` key in DataStore — resetting it replays
  onboarding after every logout.

**Theme and accent are held in memory across the logout boundary** so the UI does not snap to
defaults while the user is still looking at it. A small in-memory source sits above Local,
populated with the current values at wipe time. It **must** be cleared when the next session
starts — otherwise the previous user's values shadow whatever the new user's pull writes into
Local, reintroducing the leak the wipe exists to prevent. Nothing persists it, so a process
restart lands on defaults.

**Writes made while sync is disabled are reconciled at sign-in.** `OutboxEnqueuerImpl` silently
no-ops for demo and signed-out sessions, and nothing re-enqueues afterwards. Before the first
pull of a real session, enqueue every `sync = true` key whose `updatedAt` is newer than
`lastPushedAt`. Outbox rows carry no payload, so this is cheap, and it also closes the
write-then-crash window that no shared transaction exists to protect.

**Pre-production exception.** No one-time migration of existing DataStore values into
`config_entry` is planned: the app is not released, so losing current dev/test settings is
acceptable. This decision expires at the first production release — revisit before shipping.

## Session pointers stay out

`SessionPointers` does **not** move onto the engine, for two independent reasons.

**Nullable values are incompatible with the resolution rule.** `Pref<UserId?>` uses `null` as
a value meaning "nobody is signed in", while the engine reads `null` from a layer as "key
absent here". Putting the pointers behind keys would turn `set(null)` at logout from "cleared"
into "fall through to the layer below".

**They are session state, not configuration.** They keep their own `PreferenceStore`-backed
store, so `PreferenceStore` is not deleted and migration step 3 stops being a breaking change.

Separately — and shippable as its own issue, independent of this ADR — the store should stop
exposing `Pref<T>` to readers. There are 16 write sites, all in session-lifecycle use cases
(`Login`, `Signup`, `AnonymousLogin`, `DemoLogin`, `Logout`, `DeleteUserAccount`,
`SelectWorkspace`, `CreateWorkspace`, `PostAuthBootstrap`, `AuthLocalRepository`, plus
`SyncCoordinatorWorkspaceSyncer`), against far more read sites that currently receive a
setter they must not use:

```kotlin
interface SessionPointers {          // injected everywhere
    val currentUserId: Flow<UserId?>
    val currentWorkspaceId: Flow<WorkspaceId?>
    val currentFirebaseUid: Flow<String?>
    val hasUsedDemo: Flow<Boolean>
}

interface SessionMutator {           // injected only by lifecycle use cases
    suspend fun setCurrentUser(id: UserId?)
    suspend fun setCurrentWorkspace(id: WorkspaceId?)
    suspend fun setFirebaseUid(uid: String?)
    suspend fun setHasUsedDemo(value: Boolean)
    suspend fun clearSession()
}
```

`clearSession()` also fixes a real hazard: `LogoutUseCase` and `DeleteUserAccountUseCase`
currently write three pointers as three separate DataStore edits, so a crash in between leaves
a half-cleared session (user id null, workspace id still set).

## Module boundaries

Enforced by the dependency graph, not by convention — Kotlin cannot express "public to
`data-*`, invisible to `feature/*`". Same split rationale as `sync/api` vs `sync/default`, and
the same two-module shape: `app-config/api` carries SDK-free contracts, `app-config/default`
carries the resolution engine. (The Gradle name is `app-config`, not `config` — that directory
is already taken by `config/detekt`.)

```text
app-config/api      -> domain              # keys, codecs, Config, ConfigSource + per-layer types
app-config/default  -> app-config/api      # LayeredConfig, ConfigRegistry, assembly module
app-config/remote   -> app-config/api      # Firestore-bound RemoteGlobalConfigSource (step 4)
data-local          -> app-config/api      # implements Local + Debug sources, owns UiConfigKeys
composeApp          -> app-config/{api,default,remote}
composeAppOffline   -> app-config/{api,default}
feature/*           -> domain              # app-config is NOT on the classpath
```

`api` must stay SDK-free (no Room, DataStore or Firebase), like `sync/api`. The per-layer
types and their `Empty` objects live there, so `composeAppOffline` can bind
`RemoteGlobalConfigSource.Empty` without depending on `app-config/remote`, and `composeApp`
can declare its Build layer without depending on `data-local`.

`app-config/remote` mirrors `sync-surfer`: the only module that binds Firestore to
configuration, absent from the offline build, and not needed until step 4 — steps 1-3 ship
without it. No `no-op` module is required, since `Empty` already lives in `api`. The per-user
`UserConfigSyncPlugin` is *not* placed here: it is one of thirteen sync plugins and belongs
with them in `sync-surfer`, on the shared `PluginHelpers` / `SyncPullPriorities`
infrastructure.

The only configuration types visible to features are domain facades:

```kotlin
interface UiPreferences        // exists today, unchanged signature
interface SyncSettings         // replaces SyncFeatureFlag
interface HostCapabilities     // replaces OfflineBuildFlags + SignInFeatureConfig + TransactionCreationFeatureConfig
interface AppVersionGate       // exists today, unchanged — keeps AppConfigRepository and
                               // appConfig/mobile; see "Version gate stays as it is"
interface DebugConfigInspector // debug panel only
```

## Dependency injection

No qualifiers. Layers are distinct types declared in `api`, so Koin resolves them without
`@Named`; keys are not bound at all, so the graph does not grow with the number of flags.

Assembly lives in `app-config/default`, inside a `@Module` class — the KSP processor does not
accept `@Single` on a top-level function:

```kotlin
@Module
class ConfigModule {
    @Single
    fun config(
        debug: DebugConfigSource,
        local: LocalConfigSource,
        remoteGlobal: RemoteGlobalConfigSource,
        build: BuildConfigSource,
    ): Config = LayeredConfig(layers = listOf(debug, local, remoteGlobal, build))
}
```

Layer order is passed explicitly rather than collected via `getAll()` — precedence is a
correctness property and must not depend on module load order.

Three details the naive shape gets wrong:

- **Key groups need distinct types.** Koin indexes definitions by primary type, so several
  modules each binding a bare `ConfigKeyGroup` overwrite one another and `getAll()` returns a
  single surviving group — silently hiding most keys from the debug panel and the remote pull.
  Follow the `SyncEntityPlugin` precedent: one class per group, bound to a shared interface.

  ```kotlin
  @Single(binds = [ConfigKeyGroup::class])
  internal class UiConfigKeyGroup : ConfigKeyGroup { override val keys = UiConfigKeys.all }
  ```

- **The debug store is not a Koin binding.** A second `DataStore<Preferences>` would collide
  with the unqualified one already bound in each `SharedPlatformModule`, and both layers would
  end up reading the same file. `DebugConfigSource` creates its own through an `expect/actual`
  `createDebugOverridesDataStore()`; the raw store never enters the graph.

- **`ConfigCodec` and host keys are public API of `api`.** Kotlin rejects a public constructor
  taking an internal parameter type, so `data-local` could not pass `PaletteSourceCodec` to a
  key otherwise. `internal` stays only for keys whose sole consumers are one facade
  implementation and the registry — host keys, which the hosts themselves must enumerate, are
  public. Features still cannot reach any of it: the fence is the dependency graph, not
  visibility.

Facades keep the existing annotation style:

```kotlin
@Single(binds = [UiPreferences::class])
class UiPreferencesImpl(config: Config) : UiPreferences {
    override val isDynamicColorAvailable = platformIsDynamicColorAvailable
    override val themeMode = config.handle(UiConfigKeys.themeMode)
    override val paletteSource = config.handle(UiConfigKeys.paletteSource)
    override val containerStyle = config.handle(UiConfigKeys.containerStyle)
    override val transactionsPeriodMode = config.handle(UiConfigKeys.transactionsPeriodMode)
    override val onboardingCompleted = config.handle(UiConfigKeys.onboardingCompleted)
}
```

Hosts declare their own build layer, replacing four data-class bindings:

```kotlin
// composeApp
@Single
fun buildConfigSource() = BuildConfigSource {
    put(HostConfigKeys.isOffline, false)
    put(HostConfigKeys.signInEmailPassword, true)
    put(HostConfigKeys.signInAnonymous, true)
    put(HostConfigKeys.transferEnabled, true)
}

// composeAppOffline
single<RemoteGlobalConfigSource> { RemoteGlobalConfigSource.Empty }
```

`Pref<T>` is never a Koin binding: Koin indexes by `KClass`, so `Pref<ThemeMode>` and
`Pref<ContainerStyle>` would collide and need a qualifier each. It stays a handle obtained
from `Config` inside a facade implementation.

## Debug overrides

Shipped with the first milestone, not deferred.

- Backed by its own DataStore file (`moneysurfer_debug_overrides.preferences_pb`) so
  resetting overrides never touches user settings and overrides stay out of backup/export.
- Release builds bind `DebugConfigSource.Empty`; the layer exists in the chain but is empty.
  The conditional binding is the one DSL module in the host, using an `expect/actual`
  `isDebugBuild()` — same pattern as `defaultUseEmulator()`.
- The panel needs to know which layer won, so the engine exposes resolution details, and the
  panel consumes them pre-rendered as strings via `DebugConfigInspector` (no `ConfigKey<*>`
  leaks into `feature/settings`).

```kotlin
data class ConfigResolution<T : Any>(
    val value: T,
    val winner: ConfigLayer,
    /** Absent vs undecodable are distinct — the panel must show which. */
    val perLayer: Map<ConfigLayer, LayerValue<T>>,
)
fun <T : Any> Config.resolve(key: ConfigKey<T>): ConfigResolution<T>
```

The panel also writes, and `ConfigKey<*>` never reaches `feature/settings`, so the write path
is string-keyed and parses through the registry-resolved codec:

```kotlin
interface DebugConfigInspector {
    val rows: Flow<List<Row>>                                   // name, effective, winner, per-layer
    suspend fun override(name: String, raw: String): Result<Unit>  // fails on undecodable input
    suspend fun clearOverride(name: String)
    suspend fun resetAll()
}
```

Each row carries a value-kind hint derived from the codec (boolean, enum with its choices,
free string) so the panel can render a real control instead of a text field — `PaletteSource`'s
`PRESET:<seed>` format makes free-text entry a routine way to fail.

## Write volume

Rapid repeated writes to one key do not need debouncing at the facade. Because outbox rows
are payload-free, N queued rows for the same key all push the identical current value — they
are redundant, not incorrect. Debouncing would add a state machine at every settings screen
to fix a problem one level down.

Fix it in the outbox instead, where it also benefits every other entity: `enqueue` is a plain
`@Insert` with no dedup, and `pending()` has no `DISTINCT`, so renaming an account five times
already queues five identical pushes. Replace the insert with an insert-if-absent scoped to
`status = 'PENDING'`:

```sql
INSERT INTO pending_mutations (...)
SELECT ... WHERE NOT EXISTS (
  SELECT 1 FROM pending_mutations
  WHERE entityType = :entityType AND entityId = :entityId
    AND operation = :operation AND status = 'PENDING'
)
```

Scoping to `PENDING` is required: a write that lands while a row is `IN_FLIGHT` must produce
a new row, otherwise the change made after the push read is lost. A plain unique index cannot
express this — Room's `@Index` has no `WHERE` clause — so it is a DAO-level query.

This is a sync-wide behaviour change; it ships as its own issue, not folded into a config PR.
No current setting is driven by a continuous control, so nothing needs debouncing today.
Revisit if a slider-backed key appears.

## Rules

- Features must not depend on the `config` module. Add configuration to a domain facade
  instead of widening feature visibility.
- `Config` is injected only into facade implementations, never into a ViewModel.
- Key objects are `internal` to the module owning their facade implementation, and every
  group is registered as a `ConfigKeyGroup`.
- Writable keys are `SettingKey`; server-owned and host-owned keys are plain `ConfigKey`.
- Remote reach is opt-in: only `remoteOverridable = true` keys are served or mirrored by
  RemoteGlobal. Host-identity keys and every `SettingKey` are never remote-overridable.
- `sync = false` is required for demo/session/onboarding keys.
- `sync = true` keys are stored in Room `config_entry` (account-scoped, wiped on account
  change); `sync = false` keys stay in DataStore and are never wiped.
- Layer order is declared explicitly in one place.
- **A read never writes.** Resolving to `key.default` does not create a Local entry, `hydrate()`
  does not persist anything, and the fallback after a decode failure is not written back over the
  value that failed. Otherwise a fresh device would upload its own defaults before the first
  remote pull and win LWW against the user's real settings, and a corrupt value would be silently
  destroyed instead of staying visible to the debug panel. Reconciliation therefore also sees only
  keys the user actually wrote.
- A layer returning `null` means absent, never a falsy value. Keys are `T : Any`; "empty" is a
  codec sentinel. An undecodable stored value is absent-in-that-layer, logged, not fatal.
- `appConfig/flags` is world-readable: key names and values placed there are public, so
  unannounced feature names do not belong in it.
- Kill switches combine with user toggles in a use case, not in the KV layer.
- Platform-unrepresentable values are clamped at the facade on read, never rewritten in a
  layer — layers must stay honest for `resolve()`.

## Migration

**Status.** Steps 1-3 shipped together in issue #332, with three deviations worth knowing:

- No deprecated adapters. Every injection site migrated in the same change, so
  `OfflineBuildFlags`, `SignInFeatureConfig`, `TransactionCreationFeatureConfig`,
  `SyncFeatureFlag`, `UiSettingsDataSource` and `PrefAdapters` are gone rather than kept for a
  release.
- Hydration is reached through a `ConfigHydration` domain facade, because `AppLaunchViewModel`
  lives in `navigation`, which must not see `app-config` any more than a feature may.
- The conditional `DebugConfigSource` binding lives in `shared`'s per-platform module rather than
  in each host. It is not host-specific (both builds want overrides in debug builds), and Android
  needs the `Context` that module already resolves. `isDebugBuild()` is therefore not an
  `expect`/`actual` pair: each platform's factory uses what it has — `FLAG_DEBUGGABLE`,
  `Platform.isDebugBinary`, or `true` on the developer-only desktop build.
- The Local layer stores values under a `config.` preference prefix. `ui.onboarding_completed` used
  to be a `booleanPreferencesKey` in the same file and `Preferences.Key` equality is by name only,
  so reusing the bare name would throw on every existing install. Key *names* are unprefixed
  everywhere they are user- or wire-visible.

1. `app-config/api` (`ConfigKey`, `SettingKey`, `ConfigCodec`, `Config`, `ConfigSource` and
   the per-layer types) plus `app-config/default` (`LayeredConfig`, `ConfigRegistry`, the
   assembly module). Add `BuildConfigSource` and `LocalConfigSource`. Port
   `OfflineBuildFlags`, `SyncFeatureFlag`, `SignInFeatureConfig`,
   `TransactionCreationFeatureConfig` into keys behind `HostCapabilities` / `SyncSettings`;
   keep the old data classes as deprecated adapters for one release.
2. Debug layer, `resolve()`, `DebugConfigInspector`, QA panel behind `isDebugBuild()`.
3. Move `UiPreferences` onto `Config.handle()`; delete `UiSettingsDataSource` and
   `PrefAdapters`. `PreferenceStore` stays — it becomes the session store's private backing
   (see [Session pointers stay out](#session-pointers-stay-out)). Feature code is untouched.
4. `app-config/remote` with `RemoteGlobalConfigSource`: a **new** document `appConfig/flags`
   as a free-form map of `remoteOverridable` keys, fetched on launch and on foreground return,
   mirrored to DataStore. Tighten the `appConfig` rule to `allow get: if true; allow list: if
   false;`, mirroring `userEmails`, so the collection cannot be enumerated.
   **The app-version gate is out of scope** — see [Version gate stays as it is](#version-gate-stays-as-it-is).
5. Per-user sync: `config_entry` Room table — bump `MONEY_SURFER_DB_VERSION`, write the
   explicit migration, export the schema JSON and add a migration test, because the builder
   uses `fallbackToDestructiveMigration(dropAllTables = true)` and a missing migration wipes
   the whole local database. Add `config_entry` to `LocalDataResetRepositoryImpl.clearAll()`,
   the in-memory theme overlay and the sign-in reconciliation (see
   [Session lifecycle](#session-lifecycle)). Then `UserConfigSyncPlugin` (`entityId` = key
   name, `scopeKey` = null), the `PullScope` discriminator plus a user-scoped phase 3 in
   `PullRemoteChangesUseCaseImpl`, and the Firestore rules below.

   ```
   match /users/{uid}/config/{key} {
     allow get, list: if signedIn() && request.auth.uid == uid;
     allow create, update: if signedIn() && request.auth.uid == uid && validUserConfig();
     allow delete: if signedIn() && request.auth.uid == uid;
   }

   function validUserConfig() {
     return request.resource.data.keys().hasOnly(['value', 'updatedAt', 'clientVersionCode'])
       && request.resource.data.value is string
       && request.resource.data.value.size() <= 1024
       && typeIntOk(request.resource.data, 'updatedAt')
       && hasValidClientVersion();
   }
   ```

   Write-shape validation is not optional here: these documents are client-written and the
   pull path reads `updatedAt`, so a malformed value is exactly the poison document the
   issue-#156 guard exists to reject. `allow delete` pairs with a purge step — Firestore does
   not cascade deletes into subcollections, so `FirebaseAccountDeletionRemoteSource` must
   clear `users/{uid}/config` *before* `deleteUserDoc`, or the personal data is orphaned
   permanently and unreachable once the Auth user is gone. Add
   `firestore-tests/test/userConfig.spec.js` covering self get/list, cross-user and
   unauthenticated denial, each shape rejection, and delete.

Outbox dedup (see [Write volume](#write-volume)) is prerequisite-adjacent but independent —
file it separately so it can land and be tested against existing entities first.

## Alternatives considered

**Firebase Remote Config** (`dev.gitlive:firebase-config`). Rejected, but not for the usual
reason — it does exist for this dependency stack. It would give percentage rollouts,
conditions and A/B experiments, which this design permanently forgoes; that trade-off is
accepted deliberately. Against it: it solves only the RemoteGlobal layer, so local, host and
per-user configuration would still need an engine, and the app would carry two precedence
models instead of one; the offline build would need a stub for it anyway; and it adds a second
Firebase surface on the Desktop JVM target. If staged rollouts ever become a requirement, this
is the escape hatch to revisit.

**multiplatform-settings** (or similar) for the Local layer. Rejected: DataStore, Room and
`Pref<T>` already exist here and carry the flow semantics the facades need — the engine
reorganizes them rather than replacing the storage.

**A single map document for per-user config** (`{ key: { value, updatedAt } }`) instead of one
document per key. Rejected because per-field merge is not expressible in the current sync
pipeline, so two devices editing different settings would lose one of them; see
[Per-user sync through the outbox](#per-user-sync-through-the-outbox).

**A snapshot listener on the remote document** instead of fetch-on-launch. Rejected; see
[Version gate stays as it is](#version-gate-stays-as-it-is).

## Consequences

- Net module-graph change is roughly neutral: five new singletons (`Config` plus four
  layers) against four flag bindings and two offline no-ops removed.
- The read chain for a UI preference gets shorter, and `UiPreferencesImpl` loses its
  hand-rolled per-field codecs.
- Feature-level tests keep faking facades rather than the engine, because keys are
  `internal`. Replace the three hand-written `FakeUiPreferences` classes with one fixture in
  `domain-test-fixtures`, so adding a facade field stops breaking unrelated tests.
- Server-owned flags become releasable without a build, which makes Firestore rules and the
  `appConfig` document part of the release surface.
- One Firestore document per synced key means more documents than a single map doc, but the
  synced set is around ten keys and it buys per-key LWW for free.
- The user-scoped pull phase is new surface in `PullRemoteChangesUseCaseImpl`, which is
  currently workspace-only. It needs its own tests for the signed-out and demo paths.
- Local storage splits across Room and DataStore by `sync`. `LocalConfigSource` owns that
  routing so no caller sees it, but backup/export must be checked: `config_entry` is now
  user data in the Room database.

<!-- AI:END -->
