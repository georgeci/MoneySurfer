# Splash and first-launch onboarding

<!-- DOCS:TOC -->
## Contents
- [Splash and first-launch onboarding](#splash-and-first-launch-onboarding)
- [TL;DR for agents](#tldr-for-agents)
- [Splash](#splash)
  - [Why it exists](#why-it-exists)
  - [Compose layer](#compose-layer)
  - [Native layer](#native-layer)
- [Onboarding](#onboarding)
  - [Gating](#gating)
  - [Steps per build](#steps-per-build)
- [Start route](#start-route)
<!-- DOCS:END -->

## TL;DR for agents

- The splash exists for one reason: the nav back stack bootstraps on `Route.SignIn`, so without
  it every cold start flashes a frame of the login screen before the real start route resolves.
- There are two layers of splash — a native one per platform (before the first Compose frame) and
  a Compose one (`SurferSplash`, while `AppLaunchViewModel` decides). Both must stay in sync
  visually or the handover flickers.
- Onboarding runs before anything is seeded or any session state is read, and is gated by the
  device-scoped `ui.onboarding_completed` preference.
- Offline and online builds get different onboarding flows; the offline one also owns the
  first-run seed.

READ WHEN:
- touching the app launch path, `AppLaunchViewModel`, or `resolveStartRoute`
- changing the splash look, launch theme, or launch screen on any platform
- changing the onboarding steps, its persistence flag, or the first-run seed
- adding a new build variant that has to boot the app

<!-- AI:SECTION id=onboarding-splash task=splash,launch,navigation -->
## Splash

### Why it exists

`AppNavGraph` builds its back stack from `Route.SignIn` and then replaces it once
`AppLaunchViewModel.targetRoute` emits. That bootstrap route is visible: before the splash, an
already signed-in user saw a frame of the login screen on every cold start, and a first-run user
saw login flash before the onboarding. The splash covers exactly that gap — it is not a branding
animation and must not be given a minimum display duration.

### Compose layer

[`SurferSplash`](../../uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferSplash.kt)
is a full-screen icon + wordmark + progress indicator. It is deliberately static — no view model,
no data — so it renders on the very first frame, before Koin graph resolution finishes anywhere
else. It is shown from
[`AppNavGraph`](../../navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppNavGraph.kt)
while `targetRoute == null`, and only there.

### Native layer

Each platform draws its own splash before Compose gets a frame, so the two layers have to look
like one screen. The background everywhere is the uikit theme background — `AppColors.Background`
`#FCFDF7` light, `AppColors.Dark.Background` `#1A1C19` dark. `SurferSplash` reads it from the
theme; the native layers cannot, so they hardcode the same value and have to be updated by hand.

| Platform | Wiring |
| --- | --- |
| Android (`androidApp`, `androidApp-offline`) | `Theme.MoneySurfer.Splash` (parent `Theme.SplashScreen`) in `res/values/themes.xml`, `@color/splash_background` in `res/values{,-night}/colors.xml`, `installSplashScreen()` in `MainActivity`, `postSplashScreenTheme` crossfades into `Theme.MoneySurfer`. The AndroidX dependency is added centrally by `KmpAppConventionPlugin`. `windowSplashScreenAnimatedIcon` is `@mipmap/ic_launcher` — the whole adaptive icon, which the system masks to a circle. Not `@mipmap/ic_launcher_foreground`: that layer is a white glyph on transparent and disappears against the light background. |
| iOS | A `UILaunchScreen` dict in `iosApp/iosApp/Info.plist` (`UIColorName = LaunchBackground`, `UIImageName = LaunchIcon`), backed by the `LaunchBackground` colorset and `LaunchIcon` imageset in `Assets.xcassets`. No storyboard. `LaunchIcon` is the app icon artwork downscaled to 96/192/288 px with a 25% corner radius, matching `SurferSplash`'s 96.dp icon at 24.dp corners. |

Do **not** try to configure the iOS launch screen through the
`INFOPLIST_KEY_UILaunchScreen_BackgroundColor` / `_ImageName` build settings. They resolve in
`xcodebuild -showBuildSettings` but never reach the built `Info.plist` — the app ships with an
empty `UILaunchScreen` dict and a blank white launch screen, with no build warning. Verify a change
against the built bundle, not the build settings:

```
/usr/libexec/PlistBuddy -c "Print :UILaunchScreen" "<built>.app/Info.plist"
```

So a change to `AppColors.Background` or `AppColors.Dark.Background` is not self-contained — it
has to be mirrored into four files or the splash starts flashing: `androidApp` and
`androidApp-offline` `res/values{,-night}/colors.xml`, plus the iOS `LaunchBackground` colorset.
There is no shared token across the three platforms; they drifted apart once already.
<!-- AI:END -->

<!-- AI:SECTION id=onboarding-flow task=onboarding,first-run,navigation -->
## Onboarding

Introduced by [#173](https://github.com/georgeci/MoneySurfer/issues/173) /
[#279](https://github.com/georgeci/MoneySurfer/pull/279), modelled on the `Onboarding` design
(screen 01 "Ценность" plus an adapted screen 03 "С чего начнём?"). Lives in `feature/login`:
[`OnboardingViewModel`](../../feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/onboarding/OnboardingViewModel.kt),
`OnboardingScreen`.

### Gating

`AppLaunchViewModel` checks `uiPreferences.onboardingCompleted` **first**, before the first-run
seed and before any session pointer is read — nothing is written to disk until the app has
introduced itself. That single preference read is also what clears the splash on a first launch.

The flag is the `ui.onboarding_completed` key behind `UiPreferences`, device-scoped (not the
synced user profile) — reinstall or clear-data replays the onboarding, a new device does too. That
is intentional: onboarding is a property of the install, not of the account.

`OnboardingViewModel.finish()` writes the flag **last**, after the offline seed. If the seed throws,
the flag stays false and the onboarding replays instead of dropping the user on a screen with no
workspace behind it.

### Steps per build

| | Offline (`composeAppOffline`) | Online (`composeApp`) |
| --- | --- | --- |
| Steps | 2 — value pitch, then "where do we start?" (Cash / Card / Savings) | 1 — value pitch only |
| Progress + skip | shown | hidden (`showProgress`/`showSkip` are false for a single step) |
| Seeds a workspace | yes, in `finish()` via `FirstRunSeeder` | no — an online workspace only exists after sign-in |
| Exit | `Route.AccountCreation(firstRun = true)`, pre-filled with the picked type | `Route.SignIn` |

Skip completes the onboarding too and keeps the default selection (Cash, the "Рекомендуем" one).

Design screens 02 (personalisation) and 04 (savings forecast) are deliberately not implemented —
04 depends on bank connections, forecasts and subscription analysis the app does not have, and its
figures are design placeholders.
<!-- AI:END -->

<!-- AI:SECTION id=onboarding-start-route task=first-run,navigation,offline -->
## Start route

`resolveStartRoute` in
[`AppLaunchViewModel.kt`](../../navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppLaunchViewModel.kt)
is the whole policy for users who already passed onboarding, kept as a free function so it can be
tested without a view model scope (`StartRouteTest`):

1. no user → `SignIn`
2. no workspace → `WorkspaceSelector`
3. offline and no accounts → `AccountCreation(firstRun = true)`
4. otherwise → `Dashboard`

Rule 3 is the recovery path for a process death on the first-run account screen.
`OfflineFirstRunSeeder` pins a workspace but deliberately inserts **no** placeholder account, so an
empty offline workspace can only mean the user never finished creating their first one. The first
account's currency then becomes the workspace base currency when it is saved — which is why the
old first-run currency picker could be removed.

`SeedDefaultsUseCase` is still the seed for both builds and still owns its own repair path (a
pinned workspace missing its Cash account). Offline opts out of both with `seedCashAccount =
false`, and rule 3 is what covers the resulting crash window instead; online keeps the default
`true` and relies on that repair. Do not treat the repair branch as dead code.

`AppLaunchViewModel` is a one-shot decision, not a reactive router: after the initial emission,
screens post their own navigation side effects. The single exception is the user pointer flipping
back to null (logout, revoked session, deleted account), which bounces to `SignIn`.
<!-- AI:END -->
