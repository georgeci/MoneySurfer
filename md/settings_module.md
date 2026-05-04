

Date: 2026-04-30
Scope: `feature/settings/**`

## Map of screens

| Screen | File | VM | Status |
|---|---|---|---|
| Settings root | `SettingsScreen.kt` | `SettingsViewModel` | partly wired |
| Appearance | `appearance/AppearanceScreen.kt` | `AppearanceViewModel` | partly wired |
| Preferences | `preferences/PreferencesScreen.kt` | — | mock-only |
| Sync | `sync/SyncScreen.kt` | `SyncViewModel` | partly wired |
| Backup | `backup/BackupScreen.kt` | — | mock-only |
| About | `about/AboutScreen.kt` | — | mock-only |

Nav wiring: [AppNavGraph.kt:361-397](navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppNavGraph.kt#L361-L397). All 5 sub-routes registered. No routes for: notifications, language, region, currency, terms, privacy, licenses, help, contact, feedback, account/delete-account, restore.

---

## Buttons without logic (per screen)

### Settings root — [SettingsScreen.kt](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsScreen.kt)

| Row | Line | Issue |
|---|---|---|
| Notifications | [197-201](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsScreen.kt#L197-L201) | no `onClick`, chevron is decorative — dead row |
| Language | [202-206](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsScreen.kt#L202-L206) | no `onClick`, hard-coded pill `settings_language_pill` |
| Feedback | [237-241](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsScreen.kt#L237-L241) | no `onClick`, dead row |
| Logout | [246-251](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsScreen.kt#L246-L251) | calls `LogoutUseCase` but no `NavigateBack`/restart effect — relies on global re-routing. Also shown for anon user where it's semantically odd (no session to drop) |

Cosmetic gaps:
- `userEmailText` returns hard-coded `"anon"` — not localized, not via `stringResource` (`SettingsScreen.kt:277-282`)
- `state.userEmail` falls back via `authRemoteRepository.currentEmail()` once at init; no observation of session change

### Preferences — [PreferencesScreen.kt](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt)

Whole screen is **mock**. No VM. All state is `rememberSaveable` local; nothing reaches `domain` or `data`.

| Control | Line | What's missing |
|---|---|---|
| Language pill | [93-100](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L93-L100) | no click, no picker, no persistence |
| Region pill | [101-108](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L101-L108) | same |
| Currency pill | [112-119](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L112-L119) | same |
| Number format pill | [120-126](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L120-L126) | same |
| Hide amounts switch | [127-138](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L127-L138) | toggles only local var, lost on process death-recovery edge cases, never read elsewhere |
| Week start pill | [142-148](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L142-L148) | no click |
| Hour 12/24 pill | [149-155](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L149-L155) | no click |
| Default txn type pill | [162-168](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L162-L168) | no click |
| Auto-categorize switch | [169-180](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L169-L180) | local only |
| Round-up switch | [181-192](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/preferences/PreferencesScreen.kt#L181-L192) | local only |

### Sync — [SyncScreen.kt](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/sync/SyncScreen.kt)

VM exists for force-sync and clear-firestore. Other rows are decorative.

| Control | Line | Status |
|---|---|---|
| Cloud account pill `"Active"` | [144-149](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/sync/SyncScreen.kt#L144-L149) | hard-coded English literal, not from state |
| Auto-sync switch | [150-159](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/sync/SyncScreen.kt#L150-L159) | local `rememberSaveable`, no persistence, never read by SyncCoordinator |
| Cellular switch | [165-173](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/sync/SyncScreen.kt#L165-L173) | local only |
| Background switch | [174-182](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/sync/SyncScreen.kt#L174-L182) | local only |
| Force sync | [186-195](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/sync/SyncScreen.kt#L186-L195) | **works** — `SyncCoordinator.requestSync(MANUAL)` |
| Clear Firestore | [198-207](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/sync/SyncScreen.kt#L198-L207) | **works** — `WorkspaceSyncRepository.clearWorkspace` with confirm dialog |

### Appearance — [AppearanceScreen.kt](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/appearance/AppearanceScreen.kt)

VM only handles dynamic color. Rest is mock.

| Control | Line | Status |
|---|---|---|
| Theme System/Light/Dark | [118-140](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/appearance/AppearanceScreen.kt#L118-L140) | local `ThemeMode` enum, **never applied** to actual `AppTheme`, **not persisted**. Selection is decorative |
| Dynamic color | [142-170](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/appearance/AppearanceScreen.kt#L142-L170) | **works** — `UiPreferences.dynamicColorEnabled` |
| Seed/accent picker (5 swatches) | [172-188, 263-330](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/appearance/AppearanceScreen.kt#L172-L188) | local `seedIndex`, **never applied**, not persisted |
| Reduce motion switch | [190-203](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/appearance/AppearanceScreen.kt#L190-L203) | local only, not persisted, no a11y wiring |

`UiPreferences` interface today exposes ONLY `dynamicColorEnabled` ([UiPreferences.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/preferences/UiPreferences.kt)). To make Theme/Seed/ReduceMotion real, expand `UiPreferences` + extend `UiSettingsDataSource`.

### Backup — [BackupScreen.kt](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt)

Whole screen **mock**. No VM, no use cases, no domain interfaces for backup at all.

| Control | Line | Issue |
|---|---|---|
| Frequency pill | [86-93](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt#L86-L93) | no click |
| Encryption pill `"On"` | [94-99](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt#L94-L99) | hard-coded English |
| Location pill | [100-106](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt#L100-L106) | no click |
| Back up now | [110-115](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt#L110-L115) | no click, no use case |
| Download archive | [116-121](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt#L116-L121) | no click |
| Restore | [125-130](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt#L125-L130) | no click |
| Delete cloud backup (danger) | [134-141](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/backup/BackupScreen.kt#L134-L141) | no click — destructive intent rendered, no confirm dialog, no handler |

### About — [AboutScreen.kt](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt)

Whole screen **mock**. Reads only `appInfo.version`.

| Control | Line | Issue |
|---|---|---|
| Terms | [93-97](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L93-L97) | no click, no URL opener |
| Privacy | [98-103](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L98-L103) | no click |
| Licenses | [104-109](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L104-L109) | no click; no licenses screen |
| Help center | [113-118](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L113-L118) | no click |
| Contact | [119-124](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L119-L124) | no click, no `mailto:` opener |
| Rate app | [125-130](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L125-L130) | no click, no Play/AppStore deep-link |
| Region pill | [134-140](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L134-L140) | no click |
| Diagnostic data | [141-146](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt#L141-L146) | no click; no analytics-opt-in pref |

---

## Missing screens / flows

Sub-screens linked from settings but not built:

1. **Notifications** — root row with chevron, no destination
2. **Language picker** (app UI language) — referenced from root + Preferences
3. **Region picker** — Preferences
4. **Currency picker** — Preferences (workspace base currency? user-pref? unclear scope)
5. **Number format picker** (1,000.00 / 1.000,00 / 1 000,00)
6. **Week start picker** (Mon/Sun/Sat)
7. **Hour format picker** (12/24)
8. **Default transaction type picker** (Expense/Income/Transfer)
9. **Backup frequency picker**
10. **Backup location picker** (provider: Google Drive / iCloud / S3)
11. **Backup encryption** (key/passphrase setup)
12. **Restore from backup** flow — pick archive, preview, confirm
13. **Feedback** — form or `mailto:` action
14. **Terms** / **Privacy** — webview or external URL
15. **Open-source licenses** screen
16. **Help center** — webview or external URL
17. **Contact us** — `mailto:` or in-app form
18. **Rate app** — Play Store / App Store deep-link
19. **Region** (in About) — same picker as Preferences? clarify
20. **Diagnostic data** — analytics opt-in/out + crash reporting toggle

Sub-screens conceptually missing entirely (no entry point either):

21. **Account** — change email, change password, link/unlink Google, manage sessions
22. **Delete account** — destructive flow with workspace transfer / cancel
23. **Export data** — CSV / JSON dump (tangential to backup but distinct)
24. **Theme mode persistence + apply** — System/Light/Dark currently inert (see Appearance)
25. **Notification preferences sub-page** — categories (sync errors, invites, budget alerts), channel mapping for Android, system permission link

---

## Cross-cutting issues

- **Hard-coded English literals** in pills: `"Active"` (sync), `"On"` (backup encryption). Move to `strings.xml`.
- **Hard-coded `"anon"`** in `userEmailText` — not localized.
- **No `UiPreferences` API** for theme mode, seed, reduce-motion, hide-amounts, auto-categorize, round-up, auto-sync, cellular, background. Need to expand `UiPreferences` interface in `domain/preferences/` and bind impls in `data/datastore/UiSettingsDataSource.kt`.
- **Logout**: posts no nav side effect from `SettingsViewModel`. Either explicitly post `NavigateBack` after `logoutUseCase()` resolves, or rely on global session-driven router (then document it). Today behavior depends on whatever observes `SessionPointers.firebaseUid`.
- **Logout for anonymous user** — string `settings_logout` shown unconditionally. Hide row (or rename to "Reset local data") when `state.isAnonymousUser == true`.
- **No `pendingMutationsCount` UI** — VM observes it ([SettingsViewModel.kt:62-68](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsViewModel.kt#L62-L68)) but Settings root never renders it. Sync hub supporting text could display badge.
- **State.userEmail loaded once at init** ([SettingsViewModel.kt:47-54](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsViewModel.kt#L47-L54)) — won't react to session change while screen is open.
- **`SettingsState` is plain data class, not sealed Loading/Content** — diverges from CLAUDE.md guideline ("ViewModel state is a sealed interface (Loading / Content) annotated with `@optics`"). Settings has no async load, so probably acceptable; flag for consistency.
- **Tests** — no `SettingsViewModelTest` / `SyncViewModelTest` / `AppearanceViewModelTest` found in `commonTest`. Verify or add.

---

## Suggested priority order

1. Wire **theme mode** + **seed accent** + **reduce motion** persistence — biggest visual gap, app currently ignores user choice.
2. Implement **language** picker (app-level) — i18n-blocking.
3. Build **Account** sub-screen (email change, delete account).
4. Real **Notifications** screen (system permission + categories).
5. **Backup** module — domain interface + at least "Back up now" + "Restore" + "Delete cloud backup" with confirm.
6. **Preferences** persistence (currency, number format, week start, hour format, default txn).
7. **Sync prefs** persistence (auto-sync, cellular, background) — wire into `SyncCoordinator` policy.
8. **About** static content — terms/privacy/licenses/help/rate via URL opener (`expect`/`actual`).
9. Replace hard-coded English literals in pills.
10. Hide Logout row for anonymous users; add explicit nav effect after logout.
