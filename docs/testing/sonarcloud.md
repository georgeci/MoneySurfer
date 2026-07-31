# SonarCloud + coverage publishing

<!-- DOCS:TOC -->
## Contents
- [SonarCloud + coverage publishing](#sonarcloud--coverage-publishing)
- [What runs in CI](#what-runs-in-ci)
- [Source discovery (KMP layout)](#source-discovery-kmp-layout)
- [Required setup (one-time)](#required-setup-one-time)
- [Coverage scope](#coverage-scope)
- [Local reproduction](#local-reproduction)
- [Troubleshooting](#troubleshooting)
<!-- DOCS:END -->

Static analysis (Sonar + detekt) and JVM coverage (Kover) are published to
[SonarCloud](https://sonarcloud.io/project/overview?id=georgeci_MoneySurfer)
from the `sonar` job in [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).

## What runs in CI

The `sonar` job executes:

```bash
./gradlew detekt koverXmlReport sonar --stacktrace --continue
```

- `detekt` — produces `*/build/reports/detekt/detekt.xml` per subproject
  (consumed by Sonar via `sonar.kotlin.detekt.reportPaths`).
- `koverXmlReport` — produces aggregated `build/reports/kover/report.xml`
  in JaCoCo format (consumed by Sonar via `sonar.coverage.jacoco.xmlReportPaths`).
- `sonar` — uploads sources, detekt findings, and coverage to SonarCloud.

`--continue` keeps Kover/Sonar publishing even when detekt has violations.
The Quality Gate decision is owned by SonarCloud, not the Gradle build.

The job runs on `push` to `main`, on PRs from the same repo, and on
`workflow_dispatch`. Fork PRs are skipped (no access to `SONAR_TOKEN`).

## Source discovery (KMP layout)

Sonar Gradle plugin defaults assume `src/main/java`, which doesn't match a
KMP module. Sources/tests are configured **per subproject** in
[`build.gradle.kts`](../../build.gradle.kts) inside `subprojects { afterEvaluate { … } }`,
filtering candidate dirs (`commonMain`, `androidMain`, `iosMain`, `jvmMain`,
plus their `Test` counterparts) to whatever actually exists in that module.

> Setting `sonar.sources` only at the root project hides every subproject —
> historically that's why SonarCloud showed only ~3 folders. Don't add
> `sonar.sources` back to the root `sonar { properties { … } }` block.

## Required setup (one-time)

1. **`SONAR_TOKEN`** — generate in SonarCloud → *My Account → Security*,
   then add to *GitHub repo → Settings → Secrets and variables → Actions*.
2. **Disable Automatic Analysis** — SonarCloud project →
   *Administration → Analysis Method* → switch to **CI-based**. Otherwise
   auto-analysis fights the Gradle scan and only sees a subset of the repo.
3. **Install the SonarCloud GitHub App** on `georgeci/MoneySurfer` for PR
   decoration (inline comments + Quality Gate as a check).
4. (optional) **Branch protection on `main`** → require the
   `SonarCloud Code Analysis` check.
5. (optional) **New Code definition** in *Administration → New Code*
   (e.g. "previous version" or a date). Drives the "new code" Quality Gate.

## Coverage scope

Aggregated by the root `kover` plugin in [`build.gradle.kts`](../../build.gradle.kts)
→ `dependencies { kover(projects.*) }`. Modules **not** in that list (`:shared`,
`:sync:no-op`, `:detekt-rules`, the `*-test-fixtures`, the two Android app
modules) contribute nothing to the published coverage; they are named in
`coverageExcludedProjects` right above it, which also hands Sonar a whole-module
`sonar.coverage.exclusions` so their absence reads as "excluded" rather than 0%.
Adding a module takes three edits — see AGENTS.md → Testing Conventions.

Two further filters narrow what is counted, and they sit on different sides:

- **Kover report filters** (root `kover { reports { filters { excludes { … } } } }`)
  drop code no test can execute from the merged `report.xml` itself, so Codecov,
  the Pages HTML report and Sonar all see the same denominator: `@Preview`
  composables, the Compose compiler's `ComposableSingletons*` lambda holders,
  Room's `*_Impl` codegen, `BuildConfig`, and the desktop `main()`. Together
  these are ~9k of ~46k measured lines (`LINE` went 48.1% → 52.1% when they were
  introduced, with no test added).
- **`sonar.coverage.exclusions`**, set per module in the `subprojects { }` block,
  covers what never reaches the Kover report at all: `src/iosMain/**`. Kover
  instruments JVM bytecode, so an iOS `actual` can hold no coverage data, while
  `sonar.sources` does hand those files to Sonar — which then scores them 0% and
  drags "Coverage on New Code" down on every iOS-side change.

  The key is sent for **every** module, `**/build/**` standing in where there is
  nothing to exclude. SonarCloud resolves the key server-side when the scanner
  omits it, and the value it serves this project is `**/*` — so a module that
  said nothing had its whole coverage dropped on import while the analysis
  stayed green. That is what kept `:navigation`, `:utils`, `:sync-surfer`,
  `:app-config:*` and most of `:feature:*` at *no* coverage in SonarCloud (not
  0% — no measure at all) long after issue #272; the modules that looked healthy
  were the ones that happened to own an `src/iosMain` and therefore sent a value
  of their own. `scripts/ci/verify-sonar-coverage.sh` now fails the `sonar` job
  on a push to `main` if any module in the Kover aggregation arrives without
  coverage, because nothing else notices: the report is complete, Codecov agrees,
  and only SonarCloud's copy is empty.

Verify a filter actually bit before trusting it — patterns are matched against
bytecode names and fail silently:

```bash
./gradlew koverXmlReport && grep -c "ComposableSingletons" build/reports/kover/report.xml
```

## Local reproduction

```bash
SONAR_TOKEN=<token> ./gradlew detekt koverXmlReport sonar --continue
```

Reports land at:

- `build/reports/kover/report.xml` (XML, what Sonar reads)
- `build/reports/kover/html/index.html` (human-readable)
- `*/build/reports/detekt/detekt.xml` per subproject

## Troubleshooting

- **"SonarCloud sees only 3 folders"** — Automatic Analysis is still on, or
  someone re-added `sonar.sources` at the root project. Fix per "Required
  setup" #2 and the per-subproject discovery block.
- **No coverage shown** — `koverXmlReport` didn't run before `sonar`, the
  module under inspection isn't in the root `kover(projects.*)` list, or it is
  sending no `sonar.coverage.exclusions` and inherited the server-side `**/*`.
  Ask SonarCloud what it stored rather than guessing — a file with no coverage
  at all carries no `coverage` measure, while a genuinely uncovered one reports
  `0.0` next to a non-zero `lines_to_cover`:

  ```bash
  curl -s "https://sonarcloud.io/api/measures/component?component=georgeci_MoneySurfer:navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppNavigator.kt&metricKeys=coverage,lines_to_cover"
  ```

  The effective server-side value is visible the same way:

  ```bash
  curl -s "https://sonarcloud.io/api/settings/values?component=georgeci_MoneySurfer&keys=sonar.coverage.exclusions"
  ```
- **`SONAR_TOKEN` not set** — job logs show
  `Not authorized. Please check the property sonar.token`. Add the secret.
- **Fork PRs skipped** — by design (`if:` guard on the job). Push to a
  branch on the main repo to get analysis.
