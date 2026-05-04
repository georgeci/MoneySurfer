# Skill - Kotlin Style

<!-- DOCS:TOC -->
## Contents
- [Skill - Kotlin Style](#skill---kotlin-style)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Prefer shared KMP code in `commonMain`.
- Keep failures typed with domain errors where they cross boundaries.
- Use existing Arrow and optics patterns.
- Read this before Kotlin implementation work.

READ WHEN:
- editing Kotlin
- adding use cases
- changing ViewModels
- changing domain models

<!-- AI:SECTION id=kotlin-style task=kotlin,domain,viewmodel -->
## Rules

- Add dependencies only through `gradle/libs.versions.toml`.
- Domain IDs are UUID-backed value classes with `Companion.uuid()`.
- ViewModel state is a sealed interface with existing optics patterns where present.
- Suspend methods that can fail return `Either<DomainError, T>`.
<!-- AI:END -->
