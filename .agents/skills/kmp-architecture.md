# Skill - KMP Architecture Boundaries

Use for module moves, dependency changes, use cases, repositories, and platform
abstractions.

## Rules

- `domain`: models, repository interfaces, domain errors, use cases. No SDKs.
- `data`: Room, DataStore, Firebase, Firestore implementations.
- `shared`: ViewModels, feature wiring, navigation-facing orchestration.
- `sync`: coordinator contracts/runtime. No SDKs.
- Use `expect`/`actual` for platform APIs when shared code needs platform
  behavior.
- Add dependency versions only in `gradle/libs.versions.toml`.

## Checks

- No `data` dependency from `shared` or features.
- No Firebase/Room/DataStore imports in `domain` or `sync`.
- Repository impls bind to domain/sync interfaces via Koin annotations.
- Errors crossing data boundary are typed.
