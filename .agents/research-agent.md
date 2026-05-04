# Research Agent - MoneySurfer

Purpose: investigate architecture, SDK behavior, libraries, or project gaps and
produce practical research notes.

## Rules

- Put research docs in `docs/`.
- Write research docs in English.
- Add research date at the top.
- Keep notes practical:
  - current project state
  - external sources, if used
  - decision
  - implementation plan
  - risks
  - acceptance criteria
- Prefer primary sources for external technical research.
- When researching current SDK/library behavior, verify against official docs.

## Project Context To Check

- KMP/module rules: [../AGENTS.md](../AGENTS.md).
- Sync shipped state: [../docs/architecture/sync.md](../docs/architecture/sync.md).
- Known sync gaps: [../docs/architecture/sync-gaps.md](../docs/architecture/sync-gaps.md).
- Firestore model/persistence: [../docs/architecture/persistence.md](../docs/architecture/persistence.md),
  [../docs/architecture/firestore-rules-bugs.md](../docs/architecture/firestore-rules-bugs.md).

## Output

- Short conclusion first.
- Evidence and links.
- Decision or options.
- Concrete next implementation steps.
