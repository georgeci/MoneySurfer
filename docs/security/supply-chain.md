# Supply-chain hardening — Gradle dependencies

<!-- DOCS:TOC -->
## Contents
- [Supply-chain hardening — Gradle dependencies](#supply-chain-hardening--gradle-dependencies)
- [What is enforced now](#what-is-enforced-now)
- [Regenerating the lockfiles](#regenerating-the-lockfiles)
- [Why skiko / compose.desktop are excluded from the lock state](#why-skiko--composedesktop-are-excluded-from-the-lock-state)
- [Known gaps / future work](#known-gaps--future-work)
<!-- DOCS:END -->

Tracks [#158](https://github.com/georgeci/MoneySurfer/issues/158). Goal: stop a
compromised or republished artifact — an app dependency **or** a build plugin
from `gradlePluginPortal()` — from silently entering a release or CI build (CI
holds the Firebase config secrets and `SONAR_TOKEN`).

<!-- AI:SECTION id=supply-chain task=build,security,supply-chain -->
## What is enforced now

1. **`repositoriesMode = FAIL_ON_PROJECT_REPOS`** ([`settings.gradle.kts`](../../settings.gradle.kts)).
   No subproject (or a plugin acting on one) can add its own repository. Every
   dependency must resolve through the curated, group-scoped repositories in
   `dependencyResolutionManagement` — a rogue `repositories { }` block fails the
   build instead of widening the trusted source set.

2. **Dependency locking** ([`build.gradle.kts`](../../build.gradle.kts), the
   `allprojects { dependencyLocking { … } }` block). `lockAllConfigurations()`
   pins every resolved transitive version. The committed `gradle.lockfile` per
   module — plus the root `gradle.lockfile` and `settings-gradle.lockfile` (the
   plugin/settings classpath) — is the source of truth; a build that resolves a
   version **not** in the lock state fails with
   `dependencies … not part of the dependency lock state`.

Locking pins **versions**, not artifact **content**: a republish under the same
coordinate + version is not detected yet. That is what checksum verification (see
[future work](#known-gaps--future-work)) adds on top.

## Regenerating the lockfiles

Any change to [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) or a
`build.gradle.kts` dependency block will shift resolved versions, so the
lockfiles must be regenerated in the same commit:

```bash
./gradlew resolveAndLockAll --write-locks --no-configuration-cache
```

`resolveAndLockAll` (registered on every project) resolves all resolvable
configurations in one pass. Run it on a **macOS host** so the iOS/native
configurations are covered — a Linux-only run omits the `ios*` klib entries.
Review the diff and commit every touched `*.lockfile`.

If CI ever fails on a lock mismatch after a legitimate upgrade, that is the
control working: regenerate, commit, and the diff shows exactly which transitive
versions moved.

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

## Known gaps / future work

These were scoped out of #158's first pass and should each land as follow-ups:

1. **Checksum verification (`gradle/verification-metadata.xml`, sha256).** The
   strongest control — detects a tampered/republished artifact for a fixed
   coordinate, which locking alone does not. Deferred because a correct file
   must aggregate the artifacts resolved on **every** runner: `ubuntu-latest`
   pulls `skiko-awt-runtime-linux-x64`, `macos-15` pulls `…-macos-arm64` plus
   the iOS klibs. Generating on a single host and committing it breaks the other
   runner. Rollout:
   - `./gradlew --write-verification-metadata sha256 help` on macOS, then again
     on Linux (or in a one-off CI job), and **merge** the two files (Gradle
     appends to an existing `verification-metadata.xml`).
   - Start with `<verify-metadata>true</verify-metadata>` +
     `<verify-signatures>false</verify-signatures>` (checksums only).
   - Keep the file green in CI on both runners before enabling.

2. **PGP signature verification** (`verify-signatures=true`). Stronger than
   checksums but higher maintenance (trusted-key management, many unsigned
   artifacts need `<trusted-artifacts>` entries). Layer on after (1) is stable.

3. **`build-logic` included build.** The convention-plugin build resolves its own
   plugin classpath from `gradlePluginPortal()` and is a separate Gradle build,
   so root `allprojects` locking does not reach it. Enable
   `dependencyLocking` inside [`build-logic`](../../build-logic) and commit its
   lockfiles too.

4. **Gradle wrapper + GitHub Actions pinning** is tracked separately in
   [#164](https://github.com/georgeci/MoneySurfer/issues/164) (wrapper sha256,
   Allure download, action tags → SHAs, firebase-tools).
<!-- AI:END -->
