# MoneySurfer

[![PR checks](https://github.com/georgeci/MoneySurfer/actions/workflows/ci.yml/badge.svg)](https://github.com/georgeci/MoneySurfer/actions/workflows/ci.yml)
[![Nightly](https://github.com/georgeci/MoneySurfer/actions/workflows/nightly.yml/badge.svg)](https://github.com/georgeci/MoneySurfer/actions/workflows/nightly.yml)

A Kotlin Multiplatform personal finance app for Android, iOS, and Desktop (JVM),
built with Compose Multiplatform. Local data lives in Room; cross-device sync
goes through Firestore.

## Platforms

- **Android** — `minSdk 24`, `targetSdk 36`
- **iOS** — built from Compose Multiplatform shared UI
- **Desktop (JVM)** — runs on JDK 17+

## Stack

- Kotlin **2.4.0** / Compose Multiplatform **1.11.1**
- Gradle **9.6.0**, JDK **17**
- Room (local), Firestore + Firebase Auth (remote / sync)
- Arrow (functional error handling), Coroutines, Kotlin Serialization

## Module layout

```text
androidApp/        Android entry point
iosApp/            native iOS entry point (Xcode)
composeApp/        Compose Multiplatform host
shared/            ViewModels, screens, navigation glue
domain/            business interfaces, models, use cases
data-local/        Room and local persistence
data-remote/       Firebase / Firestore
sync/api/          sync coordinator interfaces
sync/default/      default sync coordinator implementation
sync-surfer/       sync runtime / orchestration
uikit/             design system + reusable Compose widgets
feature/           feature modules (dashboard, settings, transaction, …)
navigation/        app navigation
utils/             shared utilities
```

For the full architecture, dependency rules, and conventions see
[AGENTS.md](AGENTS.md) and [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md).

## Requirements

- JDK **17** (e.g. Temurin / Zulu)
- Android SDK with API level **36** (for Android target)
- Xcode **15+** with Kotlin CocoaPods (for iOS target)
- A Firebase project (see [Firebase setup](#firebase-setup) below)

## Build and run

### Android

```shell
./gradlew :composeApp:assembleDebug
```

Or run from your IDE using the `androidApp` configuration.

### Desktop (JVM)

```shell
./gradlew :composeApp:run
```

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run. Make sure Kotlin CocoaPods
have been resolved first (run any Gradle task once to trigger sync).

## Firebase setup

The Firebase config files (`google-services.json` for Android,
`GoogleService-Info.plist` for iOS) are **not** committed to the repository —
they are gitignored so each fork uses its own Firebase project. Without them
the Android and iOS builds will fail at the `google-services` /
`GoogleService-Info` step. The desktop (JVM) target builds without them.

To build for Android or iOS:

1. Create your own Firebase project (or two — one for dev, one for release)
   in the [Firebase console](https://console.firebase.google.com/).
2. Register an Android app (`com.georgeci.moneysurfer` or your own
   `applicationId`) and an iOS app, then download the config files into:
   - `androidApp/src/debug/google-services.json`
   - `androidApp/src/release/google-services.json`
   - `iosApp/iosApp/GoogleService-Info.plist`
3. Update [`.firebaserc`](.firebaserc) with your project IDs (the defaults
   `moneysurfer-dev` / `moneysurfer-release` are the author's projects and
   won't be writable from your machine).
4. Deploy [`firestore.rules`](firestore.rules) and
   [`firestore.indexes.json`](firestore.indexes.json) to your project:
   `firebase deploy --only firestore`.
5. Restrict your API keys in
   [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   by Android `applicationId` + SHA-1 / iOS Bundle ID, and enable
   [App Check](https://firebase.google.com/docs/app-check) before shipping a
   real release.

For local development and tests you can skip the real project entirely and
use the Firebase Emulator Suite — see
[docs/testing/firebase-emulator.md](docs/testing/firebase-emulator.md).

## Testing

Testing is documented in
[docs/testing/testing-strategy.md](docs/testing/testing-strategy.md) — which test
layers exist and which one a change belongs in. QA task reference, tooling setup
and report paths are in the [QA runbook](docs/testing/qa-runbook.md).

Common commands:

```shell
./gradlew qaCommon          # JVM unit tests across all common modules
./gradlew qaAndroidHost     # Android host-side unit tests
./gradlew qaAll             # everything
```

Firestore rules tests:

```shell
cd firestore-tests
npm test
```

## Documentation

- [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md) — short project map, read first
- [docs/architecture/overview.md](docs/architecture/overview.md) — architecture overview
- [docs/architecture/sync.md](docs/architecture/sync.md) — sync model (Room + Firestore)
- [docs/architecture/persistence.md](docs/architecture/persistence.md) — persistence
- [docs/testing/testing-strategy.md](docs/testing/testing-strategy.md) — testing: layers, conventions, commands
- [uikit/README.md](uikit/README.md) — design system rules
- [AGENTS.md](AGENTS.md) — conventions, module DAG, AI tooling instructions

## License

[MIT](LICENSE) © 2026 Georgy Balabaichkin
