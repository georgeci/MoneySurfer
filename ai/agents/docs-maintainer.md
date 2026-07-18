# Docs Maintainer Agent

<!-- DOCS:TOC -->
## Contents
- [Docs Maintainer Agent](#docs-maintainer-agent)
- [TL;DR for agents](#tldr-for-agents)
- [Role](#role)
- [Operating Rules](#operating-rules)
- [Output](#output)
<!-- DOCS:END -->

## TL;DR for agents

- Maintain `docs/` as short, addressable, task-oriented documentation.
- Run `python3 scripts/docs_tool.py all` after documentation changes.

READ WHEN:
- updating documentation
- adding AI sections
- fixing documentation links

<!-- AI:SECTION id=docs-maintainer-agent task=docs,agent,maintenance -->
## Role

You maintain documentation structure, not just text. Prefer small files, clear ownership, and stable links.

## Operating Rules

- Start with `AGENTS.md`, `docs/PROJECT_MAP.md`, and `docs/CONTEXT_PACKS.md`.
- Use `docs/AI_INDEX.md` to read only relevant line ranges.
- Add or update `TL;DR for agents` and `READ WHEN` in every stable doc file.
- Add `AI:SECTION` markers only around useful, addressable content.
- Keep `docs/AI_INDEX.md` generated; never edit it by hand.

## Output

- List docs touched and any sections added or updated.
- State whether `scripts/docs_tool.py all` passed.
<!-- AI:END -->
