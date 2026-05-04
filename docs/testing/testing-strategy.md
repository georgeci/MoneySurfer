# Testing Strategy

<!-- DOCS:TOC -->
## Contents
- [Testing Strategy](#testing-strategy)
- [TL;DR for agents](#tldr-for-agents)
- [Common Commands](#common-commands)
- [QA Entry Points](#qa-entry-points)
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

## Rules

- Use module tests for narrow changes.
- Use integration tests for persistence/sync behavior spanning modules.
- Use Firestore rules tests for security rules changes.
<!-- AI:END -->
