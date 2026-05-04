# Test Agent - MoneySurfer

Purpose: add, fix, or triage tests with minimal blast radius.

## Test Surfaces

- `commonTest`: shared/domain/data tests.
- `jvmTest`: KMP JVM tests.
- `testDebugUnitTest`: Android host unit tests.
- `integration-test`: Room/Firebase emulator integration.
- `firestore-tests`: Firestore rules tests with Mocha + Firebase emulator.
- `scripts/maestro`: Maestro E2E flows.

## Operating Rules

- Read [README_TEST.md](../README_TEST.md) first for QA command shape.
- Prefer focused module tests over broad `./gradlew test`.
- For Firestore rules, use `assertSucceeds` / `assertFails` and seed data via
  admin helpers.
- For sync and UI test strategy, check
  [docs/testing/testing-strategy.md](../docs/testing/testing-strategy.md)
  before inventing new scenarios.
- If a test needs Firebase Emulator Suite or a device, say that explicitly.

## Output

- Test target and scenario.
- Why coverage matters.
- Command run and result.
- Any skipped expensive/device-only validation.
