# Skill - Domain Feature Implementation

Use for budgets, members/invites, settings, totals, recurring, and other
domain-heavy features.

## Required Reading By Feature

- Members/invites: [../../docs/features/members-and-invites.md](../../docs/features/members-and-invites.md)
- Accounts: [../../docs/features/accounts.md](../../docs/features/accounts.md)
- Transactions/totals: [../../docs/features/transactions.md](../../docs/features/transactions.md)
- Architecture overview: [../../docs/architecture/overview.md](../../docs/architecture/overview.md)

## Rules

- Start in `domain`: model, errors, repository interface, use cases.
- Mirror persistence in `data`.
- Wire UI through `shared`/feature modules, not direct `data`.
- For synced entities, add DTO/mappers/outbox/pull/rules/tests together.
- Money uses minor units. No `Double`/`Float` for money.
- Keep storage/wire timestamps compatible unless a migration is explicitly in
  scope.
