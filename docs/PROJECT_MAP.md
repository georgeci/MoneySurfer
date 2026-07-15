# Project Map

<!-- DOCS:TOC -->
## Contents
- [Project Map](#project-map)
- [TL;DR for agents](#tldr-for-agents)
- [Module Map](#module-map)
- [Dependency Boundaries](#dependency-boundaries)
- [Documentation Areas](#documentation-areas)
<!-- DOCS:END -->

## TL;DR for agents

- MoneySurfer is a Kotlin Multiplatform finance app with shared Compose UI.
- Read this first to choose the smallest relevant docs and code areas.
- Do not use this file as a replacement for module-specific rules.
- Full implementation notes can still live in `md/` until promoted to `docs/`.

READ WHEN:
- starting a task
- choosing context files
- checking module ownership
- deciding where a change belongs

<!-- AI:SECTION id=project-map task=project,context,architecture -->
## Module Map

```text
androidApp/             Android entry point (online)
androidApp-offline/     Android entry point (offline, Firebase-free)
composeApp/             online app shell + Compose Multiplatform host
composeAppOffline/      offline app shell (no data-remote / sync runtime)
shared/                 DI composition root, app theme, navigation glue
domain/                 business interfaces, models, use cases
data-*/                 Room, DataStore, Firebase, Firestore implementations
sync/api/               SDK-free sync coordinator contracts
sync/default/           SDK-free sync runtime core (coordinator, outbox, LWW)
sync/no-op/             no-op SyncCoordinator for offline builds
sync-surfer/            Firestore-bound sync implementation (entity plugins)
uikit/                  design system and reusable Compose widgets
feature/                feature modules (account, category, dashboard, ...)
navigation/             app navigation (Navigation 3)
utils/                  small shared utilities (MviViewModel, AsyncState)
*-test-fixtures/        shared test fixtures (domain, data, sync)
integration-test/       Firebase/Room integration tests
firestore-tests/        Firestore rules tests
build-logic/            Gradle convention plugins
iosApp/                 native iOS Xcode entry point (online)
iosAppOffline/          native iOS Xcode entry point (offline)
```

## Dependency Boundaries

```text
androidApp         -> composeApp        -> shared -> feature:* -> domain -> sync:api
androidApp-offline -> composeAppOffline -> shared    feature:* -> {navigation, uikit, utils}

composeApp        -> {data-remote, sync:default, sync-surfer}   # online wiring
composeAppOffline -> {sync:api, sync:no-op}                     # offline wiring
shared            -> data-local                                 # DI wiring only
sync-surfer       -> {sync:default, data-local, data-remote}
data-*            -> domain
```

- Feature modules must not depend on `data-*`.
- `shared` may reference `data-local` only for DI wiring; no logic in
  `shared` may call data-layer types.
- `domain` must not depend on `data-*`, sync implementations, Firebase,
  Firestore, Room, or DataStore; the SDK-free `sync:api` contracts are
  allowed.
- `sync:api` and `sync:no-op` must not depend on `data-*`, Firebase,
  Firestore, Room, or DataStore; `sync:default` may use Room/WorkManager for
  its private outbox database. Firestore-bound sync code lives only in
  `sync-surfer`.
- External SDKs are touched only from `data-*`, `sync-surfer`, or platform
  entry modules.
<!-- AI:END -->

## Documentation Areas

- `AGENTS.md`: AI working rules and context economy.
- `docs/`: authoritative documentation.
- `docs/AI_INDEX.md`: generated index of addressable AI sections.
- `docs/CONTEXT_PACKS.md`: task-oriented reading sets.
- `ai/`: agent roles, skills, and prompt templates.
- `md/`: discussion notes and drafts before promotion.
