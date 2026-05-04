# Architecture Overview

<!-- DOCS:TOC -->
## Contents
- [Architecture Overview](#architecture-overview)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Keep domain rules independent from storage, network, and platform SDKs.
- Prefer shared KMP logic and UI in `commonMain`.
- Do not introduce cross-module shortcuts that violate dependency boundaries.
- Read this when a task changes module ownership or layer boundaries.

READ WHEN:
- changing module dependencies
- adding use cases
- moving code between modules
- reviewing architecture impact

<!-- AI:SECTION id=architecture-overview task=architecture,kmp,modules -->
## Rules

- Use cases live in `domain` unless they orchestrate app/navigation concerns.
- App-level orchestration can live in `shared`.
- Suspend methods that can fail return `Either<DomainError, T>` and compose with `either { ... .bind() }`.
- SDK errors crossing into domain are typed domain errors, not raw `Throwable`.
- Domain IDs are UUID-backed value classes with `Companion.uuid()`.
- Add dependencies only through `gradle/libs.versions.toml`.
<!-- AI:END -->
