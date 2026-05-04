# Skill - QA Strategy

Use for choosing validation scope, adding tests, and triaging failures.

## Required Reading

- [../../README_TEST.md](../../README_TEST.md)
- [../../docs/testing/testing-strategy.md](../../docs/testing/testing-strategy.md)
- [../../docs/testing/firebase-emulator.md](../../docs/testing/firebase-emulator.md)

## Rules

- Pick narrow validation first.
- Do not run broad `./gradlew build` unless requested.
- State when emulator/device/Maestro prerequisites are required.
- Keep failure reproduction commands copy-pastable.

## Common Commands

```bash
./gradlew qaCommon
./gradlew qaAndroidHost
./gradlew :integration-test:jvmTest
./gradlew qaIntegrationDeviceEmulator
```
