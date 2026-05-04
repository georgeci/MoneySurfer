# Transactions

<!-- DOCS:TOC -->
## Contents
- [Transactions](#transactions)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Transactions drive balances, totals, budgets, and reporting.
- Do not change amount/date/account semantics without checking derived totals.
- Read this before transaction model, UI, persistence, or calculation changes.

READ WHEN:
- changing transactions
- editing totals
- changing transaction dates
- changing transaction sync

<!-- AI:SECTION id=transactions-feature task=transactions,totals,feature -->
## Rules

- Keep derived totals consistent with transaction writes and deletes.
- Storage and wire timestamps remain `Long epochMillis`; convert at data boundaries.
- Use targeted tests for totals and account interactions.
<!-- AI:END -->
