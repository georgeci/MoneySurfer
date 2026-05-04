# ADR-003 Sync Strategy

<!-- DOCS:TOC -->
## Contents
- [ADR-003 Sync Strategy](#adr-003-sync-strategy)
- [TL;DR for agents](#tldr-for-agents)
- [Decision](#decision)
- [Why](#why)
- [Rules](#rules)
- [Consequences](#consequences)
<!-- DOCS:END -->

## TL;DR for agents

- Room is local truth.
- Firestore is backup/cross-device replication.
- Writes go through local persistence plus outbox.
- Read this before changing sync source-of-truth or conflict behavior.

READ WHEN:
- changing sync strategy
- editing outbox
- editing pull conflict policy
- changing remote schema semantics

<!-- AI:SECTION id=adr-sync-strategy task=adr,sync,outbox,pull -->
## Decision

Use local-first sync: local Room state is authoritative for the device, and Firestore is used for replication.

## Why

The app must work offline and converge across devices without making every UI write depend on network availability.

## Rules

- Local writes complete locally before remote upload.
- Pending remote changes are tracked through outbox state.
- Pull uses cursor-based LWW and tombstones.
- Known sync gaps remain documented until closed.

## Consequences

- Conflict behavior must be explicit.
- Crash recovery and queue consistency need dedicated tests.
<!-- AI:END -->
