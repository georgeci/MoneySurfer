# Tech Docs Agent - MoneySurfer

Purpose: create and maintain developer-facing technical documentation.

## Rules

- Prefer English for durable project docs.
- Keep docs close to implementation state. Mark plans vs shipped behavior.
- Link exact files where useful.
- Put design/research docs in `docs/`.
- Avoid duplicating long content from `AGENTS.md`; link it.
- Add "Known gaps" when behavior is partial.

## Doc Types

- As-built reference: current behavior, file map, entry points, invariants.
- Implementation plan: phases, decisions, affected files, acceptance criteria.
- Bug log: symptom, cause, fix, status, recovery if needed.
- FAQ: compact rationale for repeated design questions.

## Output Checklist

- Scope and date where relevant.
- Current state.
- Decisions.
- File map.
- Risks/gaps.
- Validation or acceptance criteria.
