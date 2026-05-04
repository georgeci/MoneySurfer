# ADR-002 Navigation Ownership

<!-- DOCS:TOC -->
## Contents
- [ADR-002 Navigation Ownership](#adr-002-navigation-ownership)
- [TL;DR for agents](#tldr-for-agents)
- [Decision](#decision)
- [Why](#why)
- [Rules](#rules)
- [Consequences](#consequences)
<!-- DOCS:END -->

## TL;DR for agents

- Navigation policy belongs in app/navigation glue, not leaf UI.
- Screens emit events instead of directly owning app flow.
- Do not duplicate route definitions.
- Read this before changing destination ownership or back behavior.

READ WHEN:
- changing navigation ownership
- adding routes
- editing deep links
- reviewing screen flow

<!-- AI:SECTION id=adr-navigation task=adr,navigation,screen -->
## Decision

Keep navigation contracts centralized and let feature UI expose events that the host handles.

## Why

Centralized route ownership keeps back stack behavior consistent and avoids feature modules inventing incompatible navigation rules.

## Rules

- Follow existing route patterns.
- Keep global flow decisions out of leaf composables.
- Validate changed back stack behavior.

## Consequences

- Screen APIs may need explicit callbacks.
- Navigation tests are required for behavior changes.
<!-- AI:END -->
