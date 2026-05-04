# Sync AI Summary

<!-- DOCS:TOC -->
## Contents
- [Sync AI Summary](#sync-ai-summary)
- [TL;DR for agents](#tldr-for-agents)
- [Summary](#summary)
<!-- DOCS:END -->

## TL;DR for agents

- Read before sync work.
- Room is local truth; Firestore is replication.
- Writes must not bypass the outbox model.
- Read `docs/architecture/sync.md` plus the relevant sub-doc (`sync-coordinator`, `sync-outbox`, `sync-pull-lww`, `sync-platform`, `sync-gaps`) when changing behavior.

READ WHEN:
- changing sync logic
- editing outbox writes
- changing pull or LWW behavior
- touching sync state or cancellation

<!-- AI:SECTION id=sync-summary task=sync,outbox,pull,summary -->
## Summary

- UI calls `SyncCoordinator`.
- `sync` defines SDK-free contracts.
- Implementation code handles local write, outbox drain, remote pull, cursor state, tombstones, and conflict policy.
- Do not treat backup replication as source of truth over Room without an explicit ADR update.
<!-- AI:END -->
