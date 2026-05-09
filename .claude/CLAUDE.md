# CLAUDE.md - MoneySurfer

Use [../AGENTS.md](../AGENTS.md) as the main source of truth.

## Mandatory workflow

- **Before any commit that touches `*.kt` or `*.kts`** — run `/detekt`. detekt auto-corrects formatting; remaining findings must be fixed by hand (never silenced via baseline).
- **Shipping a branch** — when the user says "ship", "push and create PR", "оформи PR", or similar, invoke `/ship`. It renames worktree-style branches to a conventional `feat|fix|chore|...` name, commits, pushes, and opens the PR in one shot. Do not push or `gh pr create` ad-hoc.
- **Tests** — kotest `StringSpec` for unit tests (`commonTest` / `jvmTest`) with kotest assertions; JUnit 4 only for instrumented `androidDeviceTest`. See AGENTS.md → Testing Conventions.
