# iosAppOffline

Xcode entry point for the offline build of MoneySurfer on iOS. Mirrors `iosApp/`
but links the `ComposeAppOffline.framework` produced by `:composeAppOffline`
(no Firebase, no remote sync — Koin is initialised with `offlineWiring`).

- Bundle id: `com.georgeci.moneysurfer.offline`
- Product name: `MoneySurferOffline`
- Gradle build phase: `:composeAppOffline:embedAndSignAppleFrameworkForXcode`
- Compose entry: `MainViewControllerKt.MainViewController()` from
  [composeAppOffline/src/iosMain/kotlin/com/georgeci/moneysurfer/offline/MainViewController.kt](../composeAppOffline/src/iosMain/kotlin/com/georgeci/moneysurfer/offline/MainViewController.kt)

## Manual steps (not done in code)

1. **Open `iosAppOffline.xcodeproj` in Xcode.** If Xcode offers to fix file
   references on first open, accept.
2. **Signing & Capabilities.** Set your development team for the
   `iosAppOffline` target. `Configuration/Config.xcconfig` keeps `TEAM_ID=`
   empty; `project.pbxproj` carries `DEVELOPMENT_TEAM = 92SLHZAN8L` copied
   verbatim from `iosApp/` so the project opens as-is. Override locally if
   needed — do not commit personal team ids.
3. **App icon.** `Assets.xcassets/AppIcon.appiconset/` is identical to the
   regular `iosApp/` icon set. If you want the offline build visually
   distinguishable on the home screen when installed side-by-side, replace
   the icons here.
4. **CI.** Covered: `.github/workflows/ios-offline.yml` builds `iosAppOffline`
   on every PR and `main` push and runs the offline golden Maestro flow
   (`scripts/maestro/offline/offline-golden.yaml`) on an iOS Simulator via
   `./gradlew qaMaestroOfflineIos`.
5. **App Store Connect.** Only needed if the offline build is shipped as a
   public release. Bundle id `com.georgeci.moneysurfer.offline` would need
   its own App Store Connect record. Not required for internal QA.

## Verification

```bash
# KMP framework
./gradlew :composeAppOffline:linkDebugFrameworkIosSimulatorArm64
```

Expect `composeAppOffline/build/bin/iosSimulatorArm64/debugFramework/ComposeAppOffline.framework`.

Then in Xcode: select the `iosAppOffline` scheme + an iOS Simulator, Build & Run.
Smoke-test offline behavior with airplane mode on; confirm Firebase is not
linked via `otool -L` on the built `.app` binary.
