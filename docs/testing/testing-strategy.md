# Testing Strategy

<!-- DOCS:TOC -->
## Contents
- [Testing Strategy](#testing-strategy)
- [TL;DR for agents](#tldr-for-agents)
- [Common Commands](#common-commands)
- [QA Entry Points](#qa-entry-points)
  - [Offline golden-path E2E](#offline-golden-path-e2e)
- [Test tags (Compose ↔ Maestro)](#test-tags-compose-maestro)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Do not run broad builds by default.
- Pick the narrowest validation that covers the edited module.
- Device integration tests need Firebase Emulator Suite and an Android device/emulator.
- Read this before adding tests or choosing QA commands.

READ WHEN:
- adding tests
- choosing validation
- changing sync or persistence
- touching Android UI flows

<!-- AI:SECTION id=testing-strategy task=testing,qa,validation -->
## Common Commands

```bash
./gradlew :moduleName:compileCommonMainKotlinMetadata
./gradlew :moduleName:compileKotlinJvm
./gradlew :moduleName:testDebugUnitTest
./gradlew :moduleName:jvmTest
./gradlew test
```

## QA Entry Points

```bash
./gradlew qaCommon
./gradlew qaAndroidHost
./gradlew qaAndroidDevice
./gradlew qaMaestro
./gradlew qaAll
```

### Offline golden-path E2E

`scripts/maestro/offline/offline-golden.yaml` (tagged `offline`) is the
end-to-end gate for the offline MVP: first launch → currency picker → seed
verified → create transaction → balance updates → Settings (no Backup/Sync/
Logout rows). It drives the `:androidApp-offline` / `:iosAppOffline` binary,
which makes zero network calls — so it needs **no Firebase emulator and no
seeded users**. The online `qaMaestro*` suites skip it via `--exclude-tags
offline`.

```bash
# Android — needs a booted emulator/device:
./gradlew qaMaestroOfflineAndroid

# iOS — needs a booted Simulator:
./gradlew qaMaestroOfflineIos
```

## Test tags (Compose ↔ Maestro)

Anchor Maestro selectors to stable identifiers, not localized text. Compose
`Modifier.testTag(...)` is the source of truth — Maestro reaches it through
the Android resource-id bridge.

**Author screens like this:**

1. Declare tag constants in a `*TestTags` object next to the screen
   (public, top-level — referenced from both production and tests).
   Use a `screen:element` namespace (e.g. `signIn:submit`) to keep them
   greppable.
2. Apply `Modifier.testTag(...)` on every node a test or Maestro flow
   needs to find: root, fields, primary actions, error/loader nodes.
3. On the screen root, attach `Modifier.surferTestTagAsId()`
   (from `uikit/.../modifier/SurferTestTagAsId.kt`). This enables
   `semantics { testTagsAsResourceId = true }` on Android and is a
   no-op on iOS/JVM. Without it Maestro cannot match by `id:`.

Reference implementation: `feature/login/.../SignInScreen.kt::SignInTestTags`
and `scripts/maestro/00_login.yaml` / `01_auth_signin.yaml`.

**Maestro selector style:**

```yaml
- tapOn:
    id: "signIn:submit"        # preferred — stable across locales/themes
- assertVisible:
    text: "Choose a workspace" # acceptable for screens not yet tagged
```

Prefer `id:` for everything inside a tagged screen. Fall back to `text:`
only when the target screen has no tags yet (track that as follow-up
work — don't sprinkle text matchers permanently).

## Rules

- Use module tests for narrow changes.
- Use integration tests for persistence/sync behavior spanning modules.
- Use Firestore rules tests for security rules changes.
<!-- AI:END -->
