# Tech Audit — Remaining Findings

Source: full project-structure analysis, 2026-07-15 (branch
`feature/kmp-project-structure-48d55c`). Already fixed on that branch and
therefore **not** listed below: duplicate `include(":navigation")`,
`api()` → `implementation()` conversion across all modules, stale
AGENTS.md / PROJECT_MAP.md / README.md (versions, module map, DAG, hard
rules).

Items are ordered by priority: impact first, then effort.

## 1. Sync push path hard-deletes instead of soft-deleting (data loss)

The only finding that loses data across devices. `MutationOperation.DELETE`
calls `firestore.delete()` instead of writing a tombstone
(`deletedAt` + bumped `updatedAt`), so cursor-based pulls on other devices
never see the deletion and keep stale rows forever.

- Push: `sync-surfer/src/commonMain/.../data/sync/UploadPendingChangesUseCaseImpl.kt`
  and the per-entity plugins in `sync-surfer/.../data/sync/plugin/`.
- Pull side already handles tombstones (`LwwConflictResolver` +
  `PullRemoteChangesUseCaseImpl`), so only the write shape must change.
- `firestore.rules` already anticipates soft-deletes
  (`hasValidClientVersion()`); verify the update-with-`deletedAt` shape is
  allowed and bump the rules version comment if rules change.
- Tombstone retention/GC can stay out of scope; note it in
  `docs/architecture/sync-gaps.md`.

Status: spawned as a separate task (chip) on 2026-07-15; belongs in its own
branch, not in structure cleanups.

## 2. Outbox robustness gaps (documented, still open)

All tracked in `docs/architecture/sync-gaps.md`; listed here for priority
only:

- No per-row backoff: `markFailed` resets to `PENDING` immediately — a
  poison mutation can busy-spin the drain loop.
- Dual-write (Room write + outbox enqueue) is not transactional; a crash
  between the two silently drops the mutation.
- No `IN_FLIGHT` reaper: a worker that dies hard leaves rows stuck.
- Per-workspace `scope` filtering disabled (global FIFO).

## 3. Client clocks drive conflict resolution

`updatedAt` is stamped from the client clock, not
`FieldValue.serverTimestamp()`. Clock skew lets a stale device win LWW
conflicts. Fix belongs together with (1) since both change the write
shape. See `docs/architecture/sync-gaps.md`.

## 4. Background sync is effectively foreground-only

- Periodic sync is a 1-minute in-process ticker in
  `navigation/.../AppLaunchViewModel.kt` — runs only while the app is open.
- Android `SyncWorker` (WorkManager) exists in `sync/default` but is never
  scheduled; iOS scheduler is a logging stub (no BGTaskScheduler).
- `NetworkMonitor` is bound to `NoOpNetworkMonitor` (always "online"), so
  offline devices burn failed sync attempts.

## 5. Coverage aggregation skips most UI code

Root `build.gradle.kts` Kover aggregation covers `feature:login` but not
the other six feature modules, and `:shared` (which doesn't apply the
kover plugin at all). Coverage numbers on SonarCloud/Codecov therefore
overstate real coverage. Decide: either add the missing modules to the
kover aggregation or document the exclusion as deliberate.

## 6. Firestore error classification by string matching

`sync-surfer/.../data/sync/SyncErrorClassifier.kt` maps
`FirebaseFirestoreException` via `message.lowercase()` substring checks
(GitLive SDK does not expose a uniform typed code cross-platform). A
second, simpler classifier exists in
`sync/default/.../sync/internal/SyncErrorClassifier.kt` — consider merging
them and adding tests around the string patterns so SDK message changes
are caught.

## 7. `AsyncState` convention exists but is unadopted

`utils/.../AsyncState.kt` and its AGENTS.md recommendation were added
together (commit `5d6f0fa`, 2026-05), but no feature uses it — all
async-load screens hand-roll `@optics sealed interface { Loading; Content }`.
Either migrate one simple screen (e.g. dashboard) to prove the pattern, or
drop the recommendation. Low priority; it is a deliberate forward-looking
convention, not dead code.

## 8. Minor / housekeeping

- `docs_tool.py check` fails on a pre-existing anchor bug:
  `docs/testing/testing-strategy.md` heading "Test tags (Compose ↔
  Maestro)" — the TOC generator and the checker disagree on slugging the
  "↔" character. Fix the tool or rename the heading.
- `koinCompiler { compileSafety = false }` in nearly every module is
  deliberate (koin-compiler 1.0.x can't see cross-module definitions);
  the full graph is covered by `KoinModuleVerificationTest` in
  `:composeApp` / `:composeAppOffline` jvmTest. Revisit when
  koin-annotations ships cross-module verification.
- `lastOutcome` of sync is in-memory only (`MutableStateFlow`); plan in
  the sync docs is to persist it via DataStore.
- Sonar skips app entry modules (`isSkipProject = true`) because the
  Sonar Gradle plugin ≤6.0.x references AGP's removed `AppExtension`;
  re-enable once the plugin supports AGP 9.
- Online `:androidApp` cannot be built locally — `google-services.json`
  is generated only in CI (`.github/actions/firebase-config`). Verify
  entry-point changes via `:androidApp-offline:compileDebugKotlin`.
