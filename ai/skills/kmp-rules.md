# Skill - KMP Rules

<!-- DOCS:TOC -->
## Contents
- [Skill - KMP Rules](#skill---kmp-rules)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Keep platform-specific APIs behind `expect`/`actual` or platform modules.
- Prefer shared logic in common source sets.
- Do not leak platform SDK types into domain.
- Read this before KMP architecture changes.

READ WHEN:
- adding platform APIs
- changing commonMain code
- editing module dependencies
- adding dependencies

<!-- AI:SECTION id=kmp-rules task=kmp,architecture,platform -->
## Rules

- Prefer shared logic and UI in `commonMain`.
- Use `expect`/`actual` for real platform APIs.
- Platform entry modules may touch platform SDKs.
- Domain and sync contracts remain SDK-free.
<!-- AI:END -->
