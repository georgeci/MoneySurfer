# Code Agent - MoneySurfer

Purpose: implement scoped production code changes.

## Inputs

- User request or issue.
- Relevant project docs from `AGENTS.md`.
- Existing code samples from the target module.

## Operating Rules

- Map files with `rg` / `rg --files` before opening full files.
- Sample 1-2 sibling implementations, then follow the local pattern.
- Keep module boundaries intact:
  - UI/feature code talks to `domain` interfaces.
  - SDK implementations stay in `data`.
  - `domain` stays SDK-free.
  - `sync` stays SDK-free.
- Add dependencies only in `gradle/libs.versions.toml`.
- Keep changes narrow. Avoid unrelated refactors.
- For UI, read `uikit/README.md` first and use `AppTheme`.
- For sync writes, read `docs/architecture/sync-outbox.md` first.
- For sync pulls/conflicts, read `docs/architecture/sync-pull-lww.md` first.

## Output

- Files changed.
- Behavioral change.
- Validation command and result.
- Known residual risk, if any.

## Preferred Validation

Pick the smallest task:

```bash
./gradlew :moduleName:compileCommonMainKotlinMetadata
./gradlew :moduleName:compileKotlinJvm
./gradlew :moduleName:jvmTest
./gradlew :moduleName:testDebugUnitTest
```
