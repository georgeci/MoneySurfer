---
description: Run detekt with auto-correct on modified Kotlin modules; fix the rest by hand
---

You are about to run detekt on the current change set. detekt has the `formatting` plugin wired in this repo, so `--auto-correct` will fix many style/formatting issues automatically. Whatever remains, you fix manually before the user commits.

## 1. Find modified Kotlin modules

Run:

```
git status --short && git diff --name-only $(git merge-base HEAD main)..HEAD
```

Build the set of Gradle modules that contain modified `*.kt`/`*.kts` files. A module is the top-level directory containing a `build.gradle.kts` (e.g. `domain`, `feature/transaction`, `sync/default`, `data-local`). Map paths to Gradle task notation: `feature/transaction` → `:feature:transaction:detekt`.

If no `.kt`/`.kts` files were touched, stop and report that detekt is not needed.

## 2. Run detekt with auto-correct

Run in parallel for the affected modules (cap at ~6 modules per invocation; if more, do everything together with the root task):

```
./gradlew <module>:detekt --auto-correct
```

If 6+ modules are affected or the changes touch root build files, just run:

```
./gradlew detekt --auto-correct
```

## 3. Inspect what detekt did and what remains

After the run:

- `git status --short` — list of files detekt auto-formatted.
- Parse the gradle output for unresolved findings (`reports/detekt/detekt.xml` or stdout). Each finding has rule + file + line.

## 4. Fix the rest manually

For every finding detekt could *not* auto-correct, read the file at the reported line and fix it. Common categories that require manual attention in this repo:

- `MagicNumber` — extract to a `private const val` with a meaningful name.
- `LongMethod` / `LongParameterList` / `ComplexCondition` — extract a helper or data class.
- `ReturnCount` — collapse to a single return or extract guard clauses.
- `TooGenericExceptionCaught` — narrow the catch.
- Naming rules (`FunctionNaming`, `VariableNaming`) — rename to match Kotlin conventions.

If a finding is a deliberate exception, suppress it with `@Suppress("RuleName")` *with a one-line comment explaining why* — do not bulk-suppress.

Do NOT regenerate baselines (`detektBaseline`) unless the user explicitly asks. Baselines hide problems; the goal here is to fix them.

## 5. Re-run detekt to confirm clean

Run the same `./gradlew ... :detekt` (without `--auto-correct`) one more time. It must succeed.

## 6. Report

One short paragraph: what was auto-fixed, what was hand-fixed, what (if anything) was suppressed and why.

## Guardrails

- Never edit `config/detekt/baseline.xml` to silence findings.
- Never edit `config/detekt/detekt.yml` to disable rules without user confirmation.
- If a `--auto-correct` run modifies files outside the scope of the user's current task (e.g. unrelated files staged previously), pause and tell the user before continuing.
