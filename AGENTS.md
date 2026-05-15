# AGENTS.md - MoneySurfer AI Tools

This is the canonical instruction file for AI tools in this repository. Keep
tool-specific files (`.claude/CLAUDE.md`, `.github/copilot-instructions.md`)
as thin wrappers that point here.

## Response Style

- Be terse. Keep technical substance, remove filler.
- Use normal, precise language for code, commits, PRs, security warnings, and
  irreversible actions.
- Prefer: "Bug in sync outbox. Fix:" over long greetings or generic summaries.

## Project Snapshot

MoneySurfer is a Kotlin Multiplatform app for Android, iOS, and Desktop (JVM)
using Compose Multiplatform.

- Package: `com.georgeci.moneysurfer`
- Kotlin: 2.3.20
- Compose Multiplatform: 1.10.3
- Gradle: 8.14.3
- Android: minSdk 24, targetSdk 36, compileSdk 36

## Module Map

```text
androidApp/             Android entry point
composeApp/             shared app shell + Compose Multiplatform host
shared/                 feature-facing ViewModels, screens, navigation glue
domain/                 business interfaces, models, use cases
data-local/             Room and local persistence implementations
data-remote/            Firebase/Firestore remote implementations
sync/                   SDK-free sync coordinator contracts
sync-impl/              sync runtime implementations
uikit/                  design system and reusable Compose widgets
feature/                feature modules
navigation/             app navigation
integration-test/       Firebase/Room integration tests
firestore-tests/        Firestore rules tests
build-logic/            Gradle convention plugins
iosApp/                 native iOS Xcode entry point
```

## Dependency DAG

```text
                  -> uikit
androidApp -> composeApp -> shared -> domain <- data-*
                            shared -> sync   <- sync-impl
```

Hard rules:

- `shared` and feature modules must not depend on `data-*`.
- `domain` must not depend on `data-*`, `sync`, Firebase, Firestore, Room, or
  DataStore.
- `sync` must not depend on `data-*`, Firebase, Firestore, Room, or DataStore.
- Use cases live in `domain` unless they orchestrate app/navigation concerns;
  app-level orchestration can live in `shared`.
- External SDKs are touched only from `data-*` or platform entry modules.
- SDK errors crossing into domain are typed domain errors, not raw
  `Throwable`.
- Suspend methods that can fail return `Either<DomainError, T>` and compose
  with `either { ... .bind() }`.

## Kotlin/KMP Conventions

- Prefer shared logic and UI in `commonMain`.
- Use `expect`/`actual` for real platform APIs.
- Add dependencies only through `gradle/libs.versions.toml`.
- Use `implementation(...)` in `build.gradle.kts`. Do not use `api(...)` — if
  a consumer module needs a transitive symbol, declare the dependency
  explicitly in that module's own `build.gradle.kts`.
- Domain IDs are UUID-backed value classes with `Companion.uuid()`.
- ViewModel state is a sealed interface (`Loading` / `Content`) with Arrow
  optics. Use field-level optics for state updates where current code does so.
- For in-flight async actions inside `Content`, use a single `inFlight: Boolean`
  flag — not per-action booleans (`isSelecting`, `isSubmitting`, `isSaving`,
  `isDeleting`). One flag per state cuts CPD duplication across feature
  ViewModels and matches the one-action-at-a-time UI invariant. If two
  concurrent actions really need distinct flags, justify it in a code comment.
- For trivial `Loading → Content` states with no extra fields on `Loading`,
  prefer `com.georgeci.moneysurfer.utils.AsyncState<C>` (`Loading` / `Content(value, pending)`)
  over a hand-rolled sealed interface.
- Domain time types: `kotlin.time.Instant` for moments (`createdAt`,
  `updatedAt`, `deletedAt`, `operationAt`, sync cursors); `LocalDate` for
  calendar dates; `YearMonth` for monthly periods; `LocalDateTime` only for
  UI input. Storage/wire keep `Long epochMillis` and ISO-8601 `String`.
  Convert in data-layer mappers, never in `domain`. See
  [docs/architecture/data-models.md](docs/architecture/data-models.md) and
  [md/time.md](md/time.md).

## Testing Conventions

- Unit tests (`commonTest`, `jvmTest`, `androidHostTest`) use kotest with the
  `StringSpec` style by default (`FunSpec` is acceptable when `withData` /
  `context` blocks materially help). Assertions are kotest matchers
  (`shouldBe`, `shouldBeInstanceOf`, etc.) — not `kotlin.test`.
- Instrumented tests (`androidDeviceTest`, on-device integration) stay on
  JUnit 4 (`@RunWith(AndroidJUnit4)`, `@Test`, `@Before`, `@After`) because
  the Android instrumentation runner doesn't host kotest specs. Assertions
  inside those tests still use kotest matchers — only the runner is JUnit.

## UI Rules

Read [uikit/README.md](uikit/README.md) before UI work.

- Use `AppTheme.materialColors`, `AppTheme.typography`, `AppTheme.shapes`, and
  `AppTheme.spacing`.
- Do not use `MaterialTheme.colorScheme.*` directly in screens/components.
- Do not hard-code `Color(0xFF...)` in screens/components.
- Atoms in `uikit` are internal container slots; public composables add click
  behavior and choose token variants.
- All Compose resource string placeholders must be indexed: `%1$s`, `%1$d`,
  `%2$s`. Never use bare `%s` or `%d`.

## Sync Rules

Read [docs/architecture/sync.AI_SUMMARY.md](docs/architecture/sync.AI_SUMMARY.md)
before touching sync. Read the full sync docs only when the summary is
insufficient.

- Room is local truth; Firestore is backup/cross-device replication.
- UI calls sync through `SyncCoordinator`.
- Write path uses local write + outbox. Read
  [docs/architecture/sync.md](docs/architecture/sync.md) before changing writes.
- Pull path uses cursor-based LWW and tombstones. Read
  [docs/architecture/sync.md](docs/architecture/sync.md) before changing pulls.
- Known gaps are referenced from [docs/architecture/sync.md](docs/architecture/sync.md);
  do not claim "fully implemented" without checking them.

## Firestore Rules

- Persistence overview: [docs/architecture/persistence.md](docs/architecture/persistence.md).
- Per-entity Domain ↔ Room ↔ Firestore inventory: [docs/architecture/data-models.md](docs/architecture/data-models.md).
- Time type policy: [md/time.md](md/time.md).
- Firestore schema notes: [md/firestore.md](md/firestore.md).
- Rules bug log: [docs/architecture/firestore-rules-bugs.md](docs/architecture/firestore-rules-bugs.md).
- App-version gate: [docs/architecture/app-version-gate.md](docs/architecture/app-version-gate.md).
- Firestore rules tests live in `firestore-tests/`.

## Documentation System

- [docs/](docs/): authoritative documentation.
- [md/](md/): discussions, drafts, audits, and notes before promotion to
  `docs/`.
- [ai/](ai/): agent roles, documentation skills, and task prompts.
- [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md): short project map; read first
  when choosing context.
- [docs/CONTEXT_PACKS.md](docs/CONTEXT_PACKS.md): task-oriented reading sets.
- [docs/AI_INDEX.md](docs/AI_INDEX.md): generated index of addressable
  `AI:SECTION` blocks.
- [.agents/](.agents/): existing app-specific agent profiles. Do not migrate or
  edit this folder unless the user explicitly asks.

Documentation commands:

```bash
python3 scripts/docs_tool.py toc
python3 scripts/docs_tool.py index
python3 scripts/docs_tool.py check
python3 scripts/docs_tool.py all
```

Do not update `docs/AI_INDEX.md` manually. Run
`python3 scripts/docs_tool.py index` or `python3 scripts/docs_tool.py all`.

## Context Economy

Before reading docs:

1. Open [docs/AI_INDEX.md](docs/AI_INDEX.md).
2. Select only sections matching the task.
3. Prefer line ranges over full files.
4. Read `TL;DR for agents` sections first.
5. Read full docs only if line ranges are insufficient.
6. Use [docs/CONTEXT_PACKS.md](docs/CONTEXT_PACKS.md) when the user names a
   context pack.
7. Do not update `docs/AI_INDEX.md` manually. Run
   `python3 scripts/docs_tool.py all`.

## Validation

Do not run broad builds by default. Pick the narrowest task that covers the
edited module.

Common commands:

```bash
./gradlew :moduleName:compileCommonMainKotlinMetadata
./gradlew :moduleName:compileKotlinJvm
./gradlew :moduleName:testDebugUnitTest
./gradlew :moduleName:jvmTest
./gradlew test
```

QA entry points:

```bash
./gradlew qaCommon
./gradlew qaAndroidHost
./gradlew qaAndroidDevice
./gradlew qaMaestro
./gradlew qaMaestroOfflineAndroid   # offline golden path, Android
./gradlew qaMaestroOfflineIos       # offline golden path, iOS Simulator
./gradlew qaAll
```

**Before any commit that touches Kotlin sources**, run copy-paste detection
locally so duplication is fixed before SonarCloud flags it on the PR:

```bash
./gradlew cpdCheck
```

Read the resulting `build/reports/cpd/cpdCheck.text`. See
[ai/skills/cpd-rules.md](ai/skills/cpd-rules.md) for how to interpret hits
and when extracting is the right fix vs. leaving repetition alone.

Firestore rules:

```bash
cd firestore-tests
npm test
```

**When editing `firestore.rules`**: bump the version comment on line 1
(`// v1.2.3 — YYYY-MM-DD`). Use semver: patch for additive/read-only rule
changes, minor for new write permissions, major for breaking structural
changes. Never skip this — it's the only way to tell which rules are deployed.

Device integration tests need Firebase Emulator Suite and an Android
emulator/device. See [README_TEST.md](README_TEST.md).

## Git Conventions

Branch names use a type prefix and a short kebab-case slug describing the
change (≤ 4 words):

- `feature/<slug>` — new functionality (e.g. `feature/adaptive-tablet-navigation`)
- `fix/<slug>` — bug fix (e.g. `fix/sync-outbox-retry`)
- `refactor/<slug>` — refactor without behaviour change
- `chore/<slug>` — build, CI, tooling
- `docs/<slug>` — documentation only
- `test/<slug>` — tests only

If the harness or a tool (Claude Code on the web, Copilot, etc.) auto-assigns
a branch like `claude/<slug>-<id>`, rename it to match this convention before
pushing or before opening a PR.

Commit messages follow Conventional Commits with the same type vocabulary
(`feat(navigation): …`, `fix(sync): …`, `docs(testing): …`). Keep the subject
≤ 70 chars; put detail in the body.

## Legacy Documentation Map

- [README.md](README.md): KMP project basics and run commands.
- [README_TEST.md](README_TEST.md): QA, Kover, Allure, integration tests,
  Maestro.
- [docs/testing/sonarcloud.md](docs/testing/sonarcloud.md): SonarCloud +
  coverage publishing (CI job, KMP source discovery, GitHub App setup).
- [uikit/README.md](uikit/README.md): design system rules.
- [docs/architecture/sync.AI_SUMMARY.md](docs/architecture/sync.AI_SUMMARY.md):
  quick sync entry point.
- [docs/architecture/sync.md](docs/architecture/sync.md): authoritative sync
  rules; sub-docs `sync-architecture`, `sync-coordinator`, `sync-outbox`,
  `sync-pull-lww`, `sync-platform`, `sync-gaps`.
- [docs/architecture/app-version-gate.md](docs/architecture/app-version-gate.md):
  app-version gate as-built.
- [docs/architecture/firestore-rules-bugs.md](docs/architecture/firestore-rules-bugs.md):
  Firestore rules bug tracker.
- [docs/features/members-and-invites.md](docs/features/members-and-invites.md):
  members + invites as-built.
- [docs/testing/firebase-emulator.md](docs/testing/firebase-emulator.md):
  emulator setup, Maestro wiring, JVM gap, troubleshooting.
- [docs/architecture/sync-coordinator.md](docs/architecture/sync-coordinator.md): original coordinator design
  draft + Design Q&A appendix (anchors `#faq-1` … `#faq-20`).
- Forward-looking drafts (still in `md/`, not yet shipped): [md/budgets.md](md/budgets.md),
  [md/members.md](md/members.md) (Phase 4–5 UI), [md/settings_module.md](md/settings_module.md),
  [md/totatl_calc.md](md/total_calc.md), [md/time.md](md/time.md),
  [md/ui_test.md](md/ui_test.md) (Phase 2), [md/test_debt.md](md/test_debt.md),
  [md/block.md](md/block.md) (startup ordering, read-only fallback).

## iOS release / TestFlight

Archive + upload to App Store Connect is driven by
[scripts/ios/release.sh](scripts/ios/release.sh):

```
scripts/ios/release.sh main       # iosApp
scripts/ios/release.sh offline    # iosAppOffline
scripts/ios/release.sh all        # main, then offline
scripts/ios/release.sh main --no-upload   # archive + export only
```

The script archives Release with automatic signing
(`-allowProvisioningUpdates`), exports an App Store `.ipa`, and uploads via
`xcrun altool` using an App Store Connect API key. Configuration via env
vars or `local.properties` (env wins):

- `ASC_API_KEY_ID` — key id from App Store Connect → Users and Access → Keys.
- `ASC_API_ISSUER_ID` — issuer uuid from the same page.
- `ASC_API_KEY_PATH` — path to `AuthKey_<id>.p8`. Keep it under
  `keystore/` (gitignored) or anywhere outside the repo.
- `ASC_TEAM_ID` — Apple team id, defaults to `92SLHZAN8L`.
- `ASC_BUILD_NUMBER` — optional. When set, passed to `xcodebuild archive` as
  `APP_VERSION_CODE=<n>` so `CURRENT_PROJECT_VERSION` (defined in
  [Version.xcconfig](Version.xcconfig)) resolves to a unique build number for
  this archive only — the working tree is not modified. TestFlight rejects
  duplicate build numbers; in CI use e.g. `ASC_BUILD_NUMBER=$(date +%s)`.

The script is unattended-friendly (no prompts) and is the same code path
intended for any future GitHub Actions workflow.

## Sub-Agents

Use `.agents/` profiles when delegating or structuring AI work:

- [.agents/code-agent.md](.agents/code-agent.md): feature/code changes.
- [.agents/test-agent.md](.agents/test-agent.md): tests and QA.
- [.agents/research-agent.md](.agents/research-agent.md): research and
  architecture investigation.
- [.agents/tech-docs-agent.md](.agents/tech-docs-agent.md): technical docs.

Use `.agents/skills/` for app-specific task packs:

- KMP architecture boundaries.
- Compose/uikit UI.
- Sync v2 and outbox.
- Firestore rules.
- QA and test strategy.
- Domain feature implementation.

Use [ai/agents/docs-maintainer.md](ai/agents/docs-maintainer.md) and
[ai/skills/docs-structure.md](ai/skills/docs-structure.md) for documentation
work.
