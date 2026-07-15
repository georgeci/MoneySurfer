# CPD — copy-paste detection (local)

<!-- DOCS:TOC -->
## Contents
- [CPD — copy-paste detection (local)](#cpd-copy-paste-detection-local)
- [When to run](#when-to-run)
- [Reports](#reports)
- [Configuration](#configuration)
- [Relation to Sonar and CI](#relation-to-sonar-and-ci)
<!-- DOCS:END -->

CPD (PMD's Copy-Paste Detector) flags duplicated Kotlin blocks the same way
SonarCloud does. It runs locally so duplication is caught **before** push,
not after Sonar comments on the PR.

## When to run

Run before every commit that adds or modifies Kotlin code:

```bash
./gradlew cpdCheck
```

It is fast (no compilation, just tokenization) and incremental.

If the task reports duplicates, read the report and decide per-case:

- Genuine duplication of logic → extract a function/class.
- Repetitive but distinct (e.g. test fixtures, exhaustive `when` branches,
  generated-looking boilerplate) → leave it; do not contort the code to
  silence CPD.

Do not raise `minimumTokenCount` to hide a real duplicate — fix the code
or accept the warning with a brief justification in the PR.

## Reports

After a run, look here:

- `build/reports/cpd/cpdCheck.text` — human-readable, easiest to skim.
- `build/reports/cpd/cpdCheck.xml` — same data, machine-readable.

Each entry lists the duplicated token count, line ranges, and source files.

## Configuration

Defined in the root [`build.gradle.kts`](../../build.gradle.kts) (search for the `cpd { ... }` block):

- `language = "kotlin"`, `minimumTokenCount = 100` — Sonar's default
  threshold for Kotlin. Smaller blocks are noise. Do not raise it to hide a
  real duplicate; Sonar still uses 100 and would flag it anyway.
- `ignoreFailures = false` — **blocking gate**: new duplication fails the
  build. The backlog was refactored away in issue #134.
- Excludes: `build/`, `generated/`, any `*Test*` source set, `iosApp/`,
  `iosAppOffline/`, and `repository/FirebaseCrashReporter.kt`. Test code
  repeats by nature; production code is what matters. The crash-reporter
  android/ios pair is byte-identical platform-`actual` boilerplate against the
  same GitLive API — an accepted false positive, not a duplicate worth
  contorting an intermediate source set to remove.

PMD version is pinned to 7.7.0 inline in the task config.

## Relation to Sonar and CI

SonarCloud runs CPD on every PR, and the PR-checks workflow (`.github/workflows/ci.yml`)
runs `./gradlew cpdCheck` as a dedicated blocking step before the JUnit run — so
duplication fails the PR locally-reproducibly, not only via a Sonar comment. The
local task is the same engine, same threshold, just earlier in the loop; run it
before committing Kotlin changes.
