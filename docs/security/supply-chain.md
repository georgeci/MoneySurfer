# Supply-chain hardening — Gradle dependencies

<!-- DOCS:TOC -->
## Contents
- [Supply-chain hardening — Gradle dependencies](#supply-chain-hardening--gradle-dependencies)
- [What is enforced now](#what-is-enforced-now)
- [Regenerating the lockfiles](#regenerating-the-lockfiles)
  - [Dependabot PRs always need this](#dependabot-prs-always-need-this)
- [Why skiko / compose.desktop are excluded from the lock state](#why-skiko--composedesktop-are-excluded-from-the-lock-state)
- [Why kotlinAbiValidationCompatClasspath is pinned by hand](#why-kotlinabivalidationcompatclasspath-is-pinned-by-hand)
- [Known gaps / future work](#known-gaps--future-work)
<!-- DOCS:END -->

Tracks [#158](https://github.com/georgeci/MoneySurfer/issues/158). Goal: stop a
compromised or republished artifact — an app dependency **or** a build plugin
from `gradlePluginPortal()` — from silently entering a release or CI build (CI
holds the Firebase config secrets and `SONAR_TOKEN`).

<!-- AI:SECTION id=supply-chain task=build,security,supply-chain -->
## What is enforced now

1. **`repositoriesMode = FAIL_ON_PROJECT_REPOS`** in both
   [`settings.gradle.kts`](../../settings.gradle.kts) and the
   [`build-logic`](../../build-logic/settings.gradle.kts) included build. No
   subproject (or a plugin acting on one) can add its own repository. Every
   dependency resolves through curated repositories; Google Maven is restricted
   to Android/Google groups.

2. **Dependency locking** ([`build.gradle.kts`](../../build.gradle.kts), the
   `allprojects { dependencyLocking { … } }` block). `lockAllConfigurations()`
   pins every resolved transitive version of each project's **dependency**
   configurations. The committed lockfiles are the source of truth; a build that
   resolves a version **not** in the lock state fails with
   `dependencies … not part of the dependency lock state`:
   - a `gradle.lockfile` per module — the app / library / test dependency graph;
   - the root `gradle.lockfile` — the root project's tooling configurations
     (Kover, CPD, Sonar, detekt, module-graph);
   - `settings-gradle.lockfile` — the settings-level resolution, which here is
     just the version catalog (`incomingCatalogForLibs0`).

3. **Buildscript (plugin) classpath locking.** The Gradle plugins applied via the
   `plugins { }` DSL (AGP, Kotlin, Compose, KSP, detekt, koin-compiler, the Play
   publisher, …) resolve on the *buildscript classpath*, which
   `lockAllConfigurations()` does not reach. Each project's classpath is pinned in
   a separate `buildscript-gradle.lockfile` by
   `resolutionStrategy.activateDependencyLocking()`:
   - for **subprojects**, activated in the `allprojects { }` block;
   - for the **root** project, activated in a top-of-file `buildscript { }` block
     — the `allprojects { }` activation runs too late, after the root classpath
     has already resolved, so the shared plugins (AGP, KGP, Compose, detekt) would
     otherwise stay unpinned.

   The separate `build-logic` build applies the same controls and commits its own
   `kmp/gradle.lockfile`, `kmp/buildscript-gradle.lockfile`, and
   `settings-gradle.lockfile`.

4. **SHA-256 dependency verification.** The root
   [`verification-metadata.xml`](../../gradle/verification-metadata.xml) and the
   included build's
   [`verification-metadata.xml`](../../build-logic/gradle/verification-metadata.xml)
   verify artifact content, so a republish under an already locked coordinate and
   version fails. Host-specific Compose Desktop/Skiko modules are the only trusted
   artifact families because their module names differ between macOS and Linux;
   their versions remain constrained by Compose and dependency locking.

## Regenerating the lockfiles

Any change to [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) or a
`build.gradle.kts` dependency block will shift resolved versions, so the
lockfiles must be regenerated in the same commit:

```bash
./gradlew resolveAndLockAll \
  --write-locks \
  --write-verification-metadata sha256 \
  --no-configuration-cache
./gradlew -p build-logic :kmp:dependencies \
  --write-locks \
  --write-verification-metadata sha256 \
  --no-configuration-cache
```

`resolveAndLockAll` (registered on every project) resolves all resolvable
configurations in one pass. Run it on a **macOS host** so the iOS/native
configurations are covered — a Linux-only run omits the `ios*` klib entries.
Review and commit every touched `*.lockfile` and
`gradle/verification-metadata.xml`.

If CI ever fails on a lock mismatch after a legitimate upgrade, that is the
control working: regenerate, commit, and the diff shows exactly which transitive
versions moved.

### Dependabot PRs always need this

Dependabot edits `gradle/libs.versions.toml` and nothing else, so **every**
Gradle PR it opens arrives red — the lock state still pins the old versions and
the build stops with `Dependency version enforced by Dependency Locking`. This
is expected, not a broken bump.

To fix one, dispatch the [`Relock dependencies`](../../.github/workflows/relock.yml)
workflow with the PR number (Actions → Relock dependencies → Run workflow). It
rebases the branch onto `main`, runs `resolveAndLockAll` on a `macos-15` runner,
and force-pushes the regenerated lockfiles back to the dependabot branch.

It is dispatch-only on purpose. `resolveAndLockAll` configures the whole build,
which means it *executes* the plugin versions the PR proposes; running that
automatically under `pull_request_target` would hand a write-scoped
`GITHUB_TOKEN` to code that arrived with the bump — the exact hole the lock
state exists to close. A human reads the version diff first, then dispatches.

## Why skiko / compose.desktop are excluded from the lock state

`compose.desktop.currentOs` (used in `:composeApp` `jvmMain`) resolves Compose's
desktop runtime, whose module **name** encodes the host OS + arch:

- `org.jetbrains.skiko:skiko-awt-runtime-macos-arm64` ↔ `…-linux-x64`
- `org.jetbrains.compose.desktop:desktop-jvm-macos-arm64` ↔ `…-linux-x64`

A single committed lockfile has to validate on both the `macos-15` and
`ubuntu-latest` CI runners. Because these two families resolve to a *different
module* per host, they are added to `dependencyLocking.ignoredDependencies` and
left unpinned. Their version is still governed transitively by the pinned Compose
plugin version, so the exposure is minimal. Everything else (JVM, Android,
common, and the Mac-only `ios*` klibs) is host-neutral and stays locked.

> If a future dependency introduces another host-variant module family (a name
> containing `-macos-`, `-linux-`, `-windows-`, `-mingw-`), a lockfile generated
> on one host will break the other runner. Add that family to
> `ignoredDependencies` (or generate + merge lockfiles on both hosts).

## Why `kotlinAbiValidationCompatClasspath` is pinned by hand

KGP 2.4 ships ABI validation, and it declares that feature's compat classpath
with a **version range** instead of the Kotlin version the build is on:

```
org.jetbrains.kotlin:kotlin-build-tools-impl:{strictly [2.4.0-Beta2, 2.5.0)}
```

Gradle resolves a range to the highest published version in it — **prereleases
included**. So `kotlin-build-tools-impl` and everything transitive through it
(`-api`, `-cri-impl`, `compiler-embeddable`, `compiler-runner`, `daemon-client`,
`daemon-embeddable`, `script-runtime`, `tooling-core`, `stdlib`) tracked
`2.4.20-Beta1`, then moved to `-Beta2` on its own — with no change on our side.

Two reasons that is not acceptable here:

1. It made every relock a ~25-file diff of churn unrelated to the change that
   triggered it, burying the lines a reviewer actually has to check — which
   defeats the point of committing the lock state at all.
2. A *prerelease* Kotlin floating into the build classpath is exactly the drift
   dependency locking exists to stop.

ABI validation is not used in this repo (no `abiValidation { }` block, no `.api`
dumps), but turning it off is not the fix: `enabled` already defaults to `false`
and KGP creates and resolves the configuration regardless. So `build.gradle.kts`
forces `kotlin-build-tools-impl` on that one configuration to the `kotlin`
version from `gradle/libs.versions.toml`. `strictly` is still satisfied because
that version lies inside the declared range, and the rest of the classpath is
transitive through `-impl` and follows it back down to the same version.

> If a Kotlin upgrade ever moves the catalog version outside the range KGP
> declares, resolution fails loudly rather than silently floating — check the
> range in the KGP release and widen the pin deliberately.

## Known gaps / future work

1. **PGP signature verification** (`verify-signatures=true`). Stronger than
   checksums but higher maintenance: trusted-key management and explicit
   exceptions for unsigned artifacts.

2. **Host-specific desktop artifacts.** Their artifact contents are trusted
   rather than checksummed because macOS and Linux resolve different module
   names. A future two-host metadata merge could remove this narrow exception.

3. **Gradle wrapper + GitHub Actions pinning** is tracked separately in
   [#164](https://github.com/georgeci/MoneySurfer/issues/164) (wrapper sha256,
   Allure download, action tags → SHAs, firebase-tools).
<!-- AI:END -->
