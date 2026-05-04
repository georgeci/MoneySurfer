# Accounts

<!-- DOCS:TOC -->
## Contents
- [Accounts](#accounts)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Accounts are core financial entities and affect transactions and balances.
- Do not change account deletion or balance semantics without checking transaction totals.
- Read this before account screens, account persistence, or account-related sync.

READ WHEN:
- changing accounts
- adding account screen
- editing account balance logic
- changing account sync

<!-- AI:SECTION id=accounts-feature task=accounts,feature,balance -->
## Rules

- Preserve transaction/account consistency.
- Validate account changes with the narrowest affected module tests.
- Capture stable account decisions in this file as they finalize.
<!-- AI:END -->
