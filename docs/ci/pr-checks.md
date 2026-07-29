# PR check fan-out

<!-- DOCS:TOC -->
## Contents
- [PR check fan-out](#pr-check-fan-out)
- [The gate](#the-gate)
  - [The full escape hatch](#the-full-escape-hatch)
- [What each PR pays for](#what-each-pr-pays-for)
- [Positive patterns only](#positive-patterns-only)
- [Adding a job or a source root](#adding-a-job-or-a-source-root)
- [Why job-level if: and not paths-ignore](#why-job-level-if-and-not-paths-ignore)
<!-- DOCS:END -->

Every PR-triggered workflow decides what to run from one shared filter,
[`.github/actions/paths-gate`](../../.github/actions/paths-gate/action.yml).
It runs [`dorny/paths-filter`](https://github.com/dorny/paths-filter) once per
workflow in a two-minute `changes` job and exposes one boolean per build target.

<!-- AI:SECTION id=pr-check-fan-out task=ci,paths-filter,workflows -->
## The gate

| Output     | True when                                                              | Gates                                                   |
| ---------- | ---------------------------------------------------------------------- | ------------------------------------------------------- |
| `plumbing` | `gradle/`, `build-logic/`, lockfiles, `.github/actions|workflows/`      | folded into every code output below                      |
| `kotlin`   | any `.kt` / `.kts`, `config/detekt/`, test resources                    | `junit`, `detekt`, `sonar` (`ci.yml`)                    |
| `android`  | `kotlin` plus Android resources, manifests, `proguard/`, `androidApp*/` | `android-compile` (`ci.yml`), CodeQL `java-kotlin` leg   |
| `ios`      | `kotlin` plus `composeAppOffline/`, `iosAppOffline/`, `Version.xcconfig`, `scripts/ios/` | `ios-offline` (`ios-offline.yml`, macos-15)    |
| `rules`    | `firestore.rules`, `firestore.indexes.json`, `firebase.json`, `firestore-tests/` | `firestore-rules` (`ci.yml`)                    |
| `js`       | any `.js` / `.ts` / `.mjs` / `.cjs` (all of it under `firestore-tests/`) | CodeQL `javascript-typescript` leg                      |
| `docs`     | inputs of `scripts/docs_tool.py check`                                  | `docs-check` (`ci.yml`)                                  |
| `any`      | any code output above is true                                           | the `full` escape hatch below                            |

`plumbing` is OR-ed into every code output inside the action, so a caller needs
exactly one token per job: a version-catalog bump or a workflow edit runs
everything, which is the intended blast radius.

### The `full` escape hatch

`ci.yml`'s `changes` job turns `any` into a `full` output, and every job below
reads `<its target> == 'true' || full == 'true'`. `full` is true for:

- **`workflow_dispatch`** — no base ref to diff against, so the filter only sees
  the last commit and means nothing. A manual run should do everything anyway.
  (`schedule` in `codeql.yml` is the same case; it forces both matrix legs.)
- **a push to `main` that touched any code** — the integration point. Two PRs
  that each legitimately skipped a different lane can only break in
  combination there, and the Allure/Kover/Sonar artifacts published from `main`
  have to be complete rather than reflect whatever the last merge touched.

A docs-only push to `main` is still cheap: `any` is false, so `full` is false.

`docs-check` is the one job gated on `docs` alone and never on `full` — a
docs-only change is exactly what every code target excludes, so it has to
stand on its own.

## What each PR pays for

| PR touches                        | Runs                                          |
| --------------------------------- | ---------------------------------------------- |
| Shared Kotlin (`domain/`, `feature/`, …) | `junit`, `detekt`, `sonar`, `android-compile`, `ios-offline`, CodeQL java-kotlin |
| `firestore.rules`, `firestore-tests/` | `firestore-rules`, CodeQL javascript-typescript |
| `iosAppOffline/` (Swift)          | `ios-offline`                                   |
| `androidApp/` resources, manifest  | `android-compile`, CodeQL java-kotlin           |
| Markdown, `.claude/`, `md/`        | `docs-check` when it is a `docs_tool` input; otherwise nothing |
| `gradle/`, `build-logic/`, `.github/` | everything                                  |

`iosApp/` — the *online* iOS app — is Swift-only and no PR job compiles it;
`ios-offline.yml` builds `iosAppOffline`, `android-compile` and CodeQL build
`:androidApp`. A Swift-only change there runs nothing on the PR. Its build
signal is in `nightly.yml` and `ios-distribute.yml`, and always was — before
this fan-out existed it merely ran unrelated lanes instead.

## Positive patterns only

`dorny/paths-filter` compiles every pattern into its own picomatch matcher and
OR-s the results (`predicate-quantifier` defaults to `some`). A `!` pattern
therefore **does not subtract**: picomatch inverts it into "matches anything
that isn't this", and the OR swallows it.

The gate was originally written in the subtractive style — `'**'` minus a list
of `'!'` doc patterns — which made it unconditionally true. The advertised
doc-only skip never fired: a README-only PR ran the JVM suite, Sonar, the
Firestore emulator and a macos-15 framework link. `actionlint.yml` now fails the
build if a `'!'` pattern reappears in the gate.

Scope a target by listing what it consumes. When in doubt, list more: a filter
that is too broad wastes runner minutes, one that is too narrow ships a break.

## Adding a job or a source root

- **New expensive job** — add the filter output it needs to the `changes` job's
  `outputs:` block and gate it with
  `needs.changes.outputs.<target> == 'true' || needs.changes.outputs.full == 'true'`.
- **New Gradle module** — nothing to do. `settings.gradle.kts` is in `plumbing`,
  so the PR that adds the module runs every lane, and `**/*.kt` covers it after.
- **New language or top-level source root** — add a filter output. Sources that
  no filter lists are invisible to CI.

## Why job-level `if:` and not `paths-ignore`

A top-level `paths-ignore` skips the whole workflow, and a required status check
that never reports blocks the merge button forever. A job skipped by its `if:`
still reports — GitHub counts a skipped required check as satisfied. Hence the
`changes` job in every PR workflow rather than a trigger-level filter.

**One exception:** `codeql.yml` selects its matrix legs dynamically, and a leg
that is absent from the matrix produces no check run at all — not a skipped one.
`main` has no required status checks today, so nothing is blocked. If
`Analyze (java-kotlin)` or `Analyze (javascript-typescript)` is ever made
required, that job has to go back to a static two-leg matrix with the per-leg
gate moved onto its steps.

The verdict is printed to the run summary as a table, so a reviewer wondering
why the iOS lane didn't run can read the answer off the job page.
<!-- AI:END -->
