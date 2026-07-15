# SonarCloud + coverage publishing

<!-- DOCS:TOC -->
## Contents
- [SonarCloud + coverage publishing](#sonarcloud-coverage-publishing)
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

Aggregated by the root `kover` plugin in [`build.gradle.kts`](../../build.gradle.kts):

```kotlin
dependencies {
    kover(projects.composeApp)
    kover(projects.domain)
    kover(projects.dataLocal)
    kover(projects.dataRemote)
    kover(projects.syncSurfer)
    kover(projects.sync.default)
    kover(projects.uikit)
}
```

Modules **not** in this list (feature modules, `:integration-test`, `:utils`,
`:navigation`, `:shared`, etc.) do not contribute to the published coverage.
Add them to the `kover(projects.*)` list to widen the scope.

`sonar.coverage.exclusions` strips Compose previews, DI modules, app entry
points, and `iosApp/**` from the coverage denominator (see
[`build.gradle.kts`](../../build.gradle.kts) → `sonar { properties { … } }`).

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
- **No coverage shown** — `koverXmlReport` didn't run before `sonar`, or the
  module under inspection isn't in the root `kover(projects.*)` list.
- **`SONAR_TOKEN` not set** — job logs show
  `Not authorized. Please check the property sonar.token`. Add the secret.
- **Fork PRs skipped** — by design (`if:` guard on the job). Push to a
  branch on the main repo to get analysis.
