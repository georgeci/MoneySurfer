# Skill - Documentation Validation

<!-- DOCS:TOC -->
## Contents
- [Skill - Documentation Validation](#skill---documentation-validation)
- [TL;DR for agents](#tldr-for-agents)
- [Commands](#commands)
- [Checks](#checks)
<!-- DOCS:END -->

## TL;DR for agents

- Generated files and links must be checked after docs edits.
- Use `scripts/docs_tool.py all` as the standard validation.
- Do not manually fix `docs/AI_INDEX.md`; fix source sections and regenerate.
- Read this before validating documentation changes.

READ WHEN:
- checking docs
- fixing broken links
- regenerating AI index
- reviewing documentation PRs

<!-- AI:SECTION id=docs-validation task=docs,validation,links,index -->
## Commands

```bash
python3 scripts/docs_tool.py index
python3 scripts/docs_tool.py toc
python3 scripts/docs_tool.py check
python3 scripts/docs_tool.py all
```

## Checks

- `AI:SECTION` blocks have unique IDs.
- `AI:SECTION` blocks have matching `AI:END`.
- Local markdown links resolve to existing files.
- Markdown anchors resolve to headings or explicit HTML anchors.
- `docs/AI_INDEX.md` matches generated output.
<!-- AI:END -->
