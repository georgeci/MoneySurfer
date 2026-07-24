# Settings — design vs. the two build variants

Date: 2026-07-22
Scope: `Settings.html` in the Claude Design project vs. `feature/settings/**`
Design mirror: [design/settings-screens.jsx](design/settings-screens.jsx) ·
inventory in [design/README.md](design/README.md)

Companion to [settings_module.md](settings_module.md) (2026-04-30), which audits
wiring gaps. This doc is only about **variant coverage**.

---

## The two variants

| | Online | Offline |
|---|---|---|
| Gradle module | `:composeApp` | `:composeAppOffline` |
| Flag binding | [`OnlineSignInModule.kt:27`](../composeApp/src/commonMain/kotlin/com/georgeci/moneysurfer/di/OnlineSignInModule.kt#L27) — `isOffline = false` | [`OfflineWiring.kt:79`](../composeAppOffline/src/commonMain/kotlin/com/georgeci/moneysurfer/offline/di/OfflineWiring.kt#L79) — `isOffline = true` |
| Remote deps | Firebase auth + Firestore | `NoOp*` bindings, no network |
| E2E | `scripts/maestro/0*.yaml` | `scripts/maestro/offline/offline-golden.yaml` |

Both variants render the **same** `SettingsScreen`; the difference is six
derived booleans on `SettingsState`
([`SettingsViewModel.kt:174-181`](../feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsViewModel.kt#L174-L181)):

```
showProfile          = !isOffline
showSyncSection      = !isOffline && syncEnabled     // ← only one with a 2nd condition
showLogout           = !isOffline
showDeleteAccount    = !isOffline
showWorkspaceMembers = !isOffline
showPendingInvites   = !isOffline
showFinishSetup      = onboardingSkipped             // variant-independent
```

**The design has no offline variant.** `Settings.html` renders a single Android
frame with a signed-in user (`Kasia Nowak`), a cloud account, sync devices and a
"Log out" row — i.e. exactly the `isOffline = false` branch. Everything below is
therefore a gap list, not a diff of two designs.

---

## Hub rows: design → online build → offline build

| Design row | Group | Online | Offline | Note |
|---|---|---|---|---|
| — | Workspace | ✅ Finish setup | ✅ | not designed; gated on `onboardingSkipped` |
| Change workspace | Workspace | ✅ | ✅ | supporting text is a static string, not the workspace name |
| Members & permissions | Workspace | ✅ | ❌ hidden | offline has no members concept |
| Invitations | Workspace | ✅ | ❌ hidden | `PendingBadge` implemented as `SurferPendingBadge` |
| — | Personalization | ✅ Categories | ✅ | not designed — real row with no mockup |
| Appearance | Personalization | ✅ | ✅ | |
| Preferences | Personalization | ✅ | ✅ | row added in #275 |
| **Notifications** | Personalization | ❌ | ❌ | designed, never built |
| **Language** | Personalization | ❌ | ❌ | designed, never built |
| Sync | Data | ✅ | ❌ hidden | also hidden online when `syncEnabled = false` |
| Backup | Data | ✅ | ✅ | row added in #275; cloud rows hidden offline |
| Export to CSV | Data | ✅ | ✅ | the one data row that works in both |
| **Security** (app lock, biometrics) | Data | ❌ | ❌ | designed, never built |
| About & legal | Help & info | ✅ | ✅ | privacy URL swaps to `URL_PRIVACY_LOCAL` offline |
| **Send feedback** | Help & info | ❌ | ❌ | designed, never built |
| Log out | — | ✅ | ❌ hidden | |
| — | — | ✅ Delete account | ❌ hidden | not designed on the hub (design puts it in Profile) |
| Version footer | — | ✅ | ✅ | |

Legend: ✅ present · ❌ absent by design/gating · ⚠️ defect.

---

## Findings

### 1. Two orphaned destinations (both variants)

`Route.SettingsPreferences` and `Route.SettingsBackup` are registered in
[`SettingsNavGraph.kt:49,63`](../feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsNavGraph.kt#L49),
`SettingsScreen` accepts `onNavigateToPreferences` / `onNavigateToBackup`, and
`SettingsViewModel` maps `OnPreferencesClick` / `OnBackupClick` to effects — but
**no composable in `SettingsScreen.kt` emits either event**. `PreferencesScreen`,
`PreferencesViewModel`, `BackupScreen`, `BackupViewModel` and the
per-platform `BackupPickerLauncher` are all dead code from the user's point of view.

The offline golden flow even documents the Backup half as intentional
(`offline-golden.yaml`: *"There is no Backup row in the Settings screen in any
build"*) without noting that the screen behind it ships anyway.

**Resolved (issue #275).** `SettingsScreen` now emits `OnPreferencesClick` from a
Preferences row in *Personalization* and `OnBackupClick` from a Backup row in
*Data*, both tagged (`SettingsTestTags.PreferencesRow` / `BackupRow`) and composed
in both variants. The stale `offline-golden.yaml` comment is gone.

### 2. The `Data` group is empty-ish offline

Offline hides Sync, and Backup has no row — so the whole `Data` section
collapses to a single **Export to CSV** row. Worth deciding whether the section
header still earns its place, or whether Backup should be *the* offline data row
(local `.ledger` archive + restore, no cloud) — the design's `BackupScreen`
already separates "Manual → Download a copy" from the cloud rows, so an
offline-safe subset is available without new visuals.

**Resolved (issue #275).** Backup *is* the second offline data row. `BackupState`
gained `showCloudBackup = !isOffline`, which hides the cloud hero, the whole
Schedule group, "Back up now" and "Delete cloud backup" offline — leaving the
local `BackupExporter`/`BackupImporter` pair (Download a copy + Restore) and a
local hero in their place.

### 3. Sync gating is asymmetric with the rest

`showSyncSection` is the only flag that also consults `syncFeatureFlag.enabled`.
That means the online build with the flag off looks *almost* like the offline
build — but still shows Profile, Members, Invitations, Logout and Delete account.
`SettingsStateOfflineTest` pins this deliberately ("gated by `isOffline`, not the
sync flag"), so it is a decision, not a bug — but it is the state least covered
by either the design or the Maestro suites.

### 4. Offline binary still compiles the online-only screens

`settingsNavGraph` is shared via [`shared/App.kt:57`](../shared/src/commonMain/kotlin/com/georgeci/moneysurfer/App.kt#L57),
so `SyncScreen` and `DeleteUserAccountScreen` ship inside `:composeAppOffline`,
backed by `NoOpPendingMutationQueue` / `NoOpUserAccountDeletionRepository`. They
are unreachable through the UI, so this is size/clarity debt rather than a
correctness problem — but any future deep link would land on a dead screen.

### 5. Design details not yet honoured (online)

- **Profile is a dead end.** The design's `NameBlock` opens `UserProfileScreen`
  (display name, email, time zone, password, 2FA, sign-in alerts, active
  sessions, download-my-data, delete account). The build renders `SurferNameBlock`
  with `trailing = null` and no `onClick` — and shows the hard-coded
  `settings_user_name` string instead of the real display name.
- **`userEmailText` returns the literal `"anon"`**
  ([`SettingsScreen.kt:327-332`](../feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsScreen.kt#L327-L332)) —
  unlocalized, and it is the *fallback* branch too, so a signed-in user with no
  email also reads "anon". Only reachable online (`showProfile` is false offline).
- **Hub supporting text is static.** The design derives every supporting line
  from live state (`Currently: Family`, `4 members`, `Synced 4 minutes ago`,
  `EUR · Monday · 24h`). Only Members and Invitations do this today; Change
  workspace, Sync and Appearance use fixed strings.
- **Delete account placement.** The design keeps it in the profile "Danger zone";
  the build stacks it under Log out on the hub. Not wrong, but it means the hub
  ends in two consecutive `danger` rows.

---

## Suggested next steps

1. Add the missing **Preferences** row to `Personalization` and decide Backup's
   fate (row, or delete the screen + route) — closes finding 1 in both variants.
2. Give the hub an **offline-aware `Data` section**: local backup/restore instead
   of the hidden Sync row.
3. Wire `NameBlock` → a real profile screen for the online build; until then,
   replace the `"anon"` literal with a `stringResource`.
4. Extend `offline-golden.yaml` to assert `settings:csvRow` visible and (once
   added) `settings:preferencesRow` visible, so the offline row set is pinned
   positively, not only by `notVisible`.
