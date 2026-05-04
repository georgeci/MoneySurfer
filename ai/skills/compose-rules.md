# Skill - Compose Rules

<!-- DOCS:TOC -->
## Contents
- [Skill - Compose Rules](#skill---compose-rules)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Use the project `uikit` tokens and components.
- Do not use `MaterialTheme.colorScheme.*` directly in screens/components.
- Do not hard-code screen/component colors.
- Read this before UI work.

READ WHEN:
- adding screen
- changing Compose UI
- adding reusable components
- reviewing UI changes

<!-- AI:SECTION id=compose-rules task=ui,screen,compose -->
## Rules

- Read `uikit/README.md` before UI work.
- Use `AppTheme.materialColors`, `AppTheme.typography`, `AppTheme.shapes`, and `AppTheme.spacing`.
- Atoms in `uikit` are internal container slots; public composables add click behavior and choose token variants.
- All Compose resource string placeholders must be indexed.
<!-- AI:END -->
