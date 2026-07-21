# CLAUDE.md - MoneySurfer

Use [../AGENTS.md](../AGENTS.md) as the main source of truth.

## Mandatory workflow

- **Before any commit that touches `*.kt` or `*.kts`** — run `/detekt`. detekt auto-corrects formatting; remaining findings must be fixed by hand (never silenced via baseline).
- **Shipping a branch** — when the user says "ship", "push and create PR", "оформи PR", or similar, invoke `/ship`. It renames worktree-style branches to a conventional `feat|fix|chore|...` name, commits, pushes, and opens the PR in one shot. Do not push or `gh pr create` ad-hoc.
- **Never force push** — `git push --force`, `-f`, `--force-with-lease`, `--force-if-includes` and `+refspec` are forbidden under any circumstances, on any branch, including after a rebase or a failed push. Blocked by a `PreToolUse` hook and `permissions.deny` in [.claude/settings.json](settings.json), and by the git [`.githooks/pre-push`](../.githooks/pre-push) hook (`git config core.hooksPath .githooks` — one-time, per clone). Never pass `--no-verify`. If history really must be rewritten, stop and ask the user to do it manually.
- **Tests** — kotest `StringSpec` for unit tests (`commonTest` / `jvmTest`) with kotest assertions; JUnit 4 only for instrumented `androidDeviceTest`. See AGENTS.md → Testing Conventions.
