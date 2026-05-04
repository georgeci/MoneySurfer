# Sync

<!-- DOCS:TOC -->
## Contents
- [Sync](#sync)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [Source Notes](#source-notes)
<!-- DOCS:END -->

## TL;DR for agents

- Room is local truth; Firestore is backup and cross-device replication.
- UI talks to sync through `SyncCoordinator`.
- Write path uses local write plus outbox.
- Read this before changing sync contracts, outbox writes, pulls, cursors, or conflict handling.

READ WHEN:
- changing sync logic
- editing outbox writes
- changing pull or LWW behavior
- touching sync state or cancellation

<!-- AI:SECTION id=sync-rules task=sync,outbox,pull,lww -->
## Rules

- `sync` stays SDK-free.
- Firestore, Room, and DataStore implementations stay outside `sync`.
- Write path uses local persistence first, then pending mutation enqueue.
- Pull path uses cursor-based last-write-wins and tombstones.
- Known gaps must be checked before claiming sync is complete.

## Source Notes

Authoritative sub-docs (start here for any non-trivial sync change):

- [Architecture](sync-architecture.md) — module DAG, file layout, DI, error-handling convention.
- [Coordinator](sync-coordinator.md) — actor loop, request merging, cancel tokens, state vs `lastOutcome`.
- [Outbox](sync-outbox.md) — dual-write, enqueue gates, drain cycle, status machine, atomicity caveat.
- [Pull / LWW / Tombstones](sync-pull-lww.md) — cursor-based pull, conflict resolver, tombstone propagation, outbox bypass.
- [Platform layer](sync-platform.md) — `BackgroundSyncScheduler` per platform, `NetworkMonitor`, `SyncTelemetry`, `AppVersionGate`, `SessionShutdownGate`.
- [Gaps](sync-gaps.md) — divergences from the original Phase-0 sync plan, NoOps still in place, follow-up work.
<!-- AI:END -->
