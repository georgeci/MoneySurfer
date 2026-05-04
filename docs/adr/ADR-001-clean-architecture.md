# ADR-001 Clean Architecture Boundaries

<!-- DOCS:TOC -->
## Contents
- [ADR-001 Clean Architecture Boundaries](#adr-001-clean-architecture-boundaries)
- [TL;DR for agents](#tldr-for-agents)
- [Decision](#decision)
- [Why](#why)
- [Rules](#rules)
- [Consequences](#consequences)
<!-- DOCS:END -->

## TL;DR for agents

- Domain stays independent of data, sync runtime, and external SDKs.
- Feature and shared code depend inward, not on storage implementations.
- Do not add convenience dependencies that violate the DAG.
- Read this before changing module dependencies.

READ WHEN:
- changing module dependencies
- adding repositories
- adding use cases
- moving code across layers

<!-- AI:SECTION id=adr-clean-architecture task=adr,architecture,modules -->
## Decision

Use strict KMP module boundaries with domain at the center and SDK access at data/platform edges.

## Why

The app needs shared business logic across Android, iOS, and Desktop while keeping storage and platform SDKs replaceable.

## Rules

- `domain` has no dependency on storage, sync runtime, or SDK modules.
- `sync` contracts remain SDK-free.
- Data/platform modules translate SDK errors into typed domain errors.

## Consequences

- Some orchestration requires explicit interfaces.
- Dependency additions must be reviewed against the module DAG.
<!-- AI:END -->
