# Context Packs

<!-- DOCS:TOC -->
## Contents
- [Context Packs](#context-packs)
- [TL;DR for agents](#tldr-for-agents)
- [Add Compose screen](#add-compose-screen)
- [Change app launch, splash or onboarding](#change-app-launch-splash-or-onboarding)
- [Change sync logic](#change-sync-logic)
- [Change persistence or Firestore rules](#change-persistence-or-firestore-rules)
- [Update documentation](#update-documentation)
<!-- DOCS:END -->

## TL;DR for agents

- Use context packs to avoid loading unrelated documentation.
- Start with `AGENTS.md`, `docs/PROJECT_MAP.md`, then the smallest matching pack.
- Prefer `*.AI_SUMMARY.md` before full docs.
- Do not edit generated `docs/AI_INDEX.md` manually.

READ WHEN:
- user says "Use context pack"
- selecting docs for a task
- planning documentation reads
- avoiding broad context loading

<!-- AI:SECTION id=context-packs task=context,docs,agent -->
## Add Compose screen

Read:
- `AGENTS.md` — UI Rules, Testing Conventions
- `docs/PROJECT_MAP.md`
- `docs/architecture/navigation.AI_SUMMARY.md`
- `uikit/README.md`

## Change app launch, splash or onboarding

Read:
- `AGENTS.md` — UI Rules, Testing Conventions
- `docs/PROJECT_MAP.md`
- `docs/features/onboarding.md`
- `docs/architecture/navigation.AI_SUMMARY.md`

## Change sync logic

Read:
- `AGENTS.md` — Dependency DAG, Sync Rules, Testing Conventions
- `docs/PROJECT_MAP.md`
- `docs/architecture/sync.AI_SUMMARY.md`
- `docs/adr/ADR-003-sync-strategy.md`

## Change persistence or Firestore rules

Read:
- `AGENTS.md` — Dependency DAG, Firestore Rules, Testing Conventions
- `docs/PROJECT_MAP.md`
- `docs/architecture/persistence.md`

## Update documentation

Read:
- `AGENTS.md`
- `docs/PROJECT_MAP.md`
- `docs/CONTEXT_PACKS.md`
- `ai/agents/docs-maintainer.md`
- `ai/skills/docs-structure.md`
- `ai/skills/docs-validation.md`
<!-- AI:END -->
