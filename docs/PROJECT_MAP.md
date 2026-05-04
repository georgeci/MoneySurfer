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
androidApp/             Android entry point
composeApp/             shared app shell + Compose Multiplatform host
shared/                 feature-facing ViewModels, screens, navigation glue
domain/                 business interfaces, models, use cases
data-*/                 Room, DataStore, Firebase, Firestore implementations
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

## Dependency Boundaries

```text
                  -> uikit
androidApp -> composeApp -> shared -> domain <- data-*
                            shared -> sync   <- sync-impl
```

- `shared` and feature modules must not depend on `data-*`.
- `domain` must not depend on `data-*`, `sync`, Firebase, Firestore, Room, or DataStore.
- `sync` must not depend on `data-*`, Firebase, Firestore, Room, or DataStore.
- External SDKs are touched only from `data-*` or platform entry modules.
<!-- AI:END -->

## Documentation Areas

- `AGENTS.md`: AI working rules and context economy.
- `docs/`: authoritative documentation.
- `docs/AI_INDEX.md`: generated index of addressable AI sections.
- `docs/CONTEXT_PACKS.md`: task-oriented reading sets.
- `ai/`: agent roles, skills, and prompt templates.
- `md/`: discussion notes and drafts before promotion.
