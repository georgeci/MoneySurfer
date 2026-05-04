# Persistence

<!-- DOCS:TOC -->
## Contents
- [Persistence](#persistence)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Keep persistence implementations behind domain or sync-facing interfaces.
- Do not leak Room, DataStore, Firebase, or Firestore types into `domain`.
- Firestore rules and schema must evolve together.
- Read this when changing storage, wire models, rules, or migrations.

READ WHEN:
- changing Room schema
- changing Firestore schema
- editing DataStore usage
- changing persistence repositories

<!-- AI:SECTION id=persistence-rules task=persistence,room,firestore,datastore -->
## Rules

- Storage and wire timestamps currently remain `Long epochMillis`.
- Convert to richer date/time types at data boundaries when used in domain/UI.
- Rules bug log: see [firestore-rules-bugs.md](firestore-rules-bugs.md).
- App-version gate behavior: see [app-version-gate.md](app-version-gate.md).
<!-- AI:END -->
