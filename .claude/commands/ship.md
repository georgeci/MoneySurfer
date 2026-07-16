---
description: Rename current branch by convention, commit, push, open PR
---

You are about to ship the current branch as a PR. The current branch is likely a random worktree name (e.g. `cowork/foo-bar-1234`) that must be replaced with a proper conventional name before pushing.

Follow these steps in order. Do not skip steps.

## 1. Inspect state (run in parallel)

- `git status --short`
- `git diff --stat` (unstaged + staged)
- `git diff --stat $(git merge-base HEAD main)..HEAD` — what this branch contains vs main
- `git log --oneline $(git merge-base HEAD main)..HEAD` — commits on this branch
- `git branch --show-current`
- `git log --oneline -10 main` — for commit-message style

## 2. Decide the conventional prefix

Pick exactly one based on the dominant nature of the changes (look at *all* commits on the branch + uncommitted diff, not just the latest):

- `feat/` — new user-visible functionality
- `fix/` — bug fix
- `chore/` — tooling, deps, config, baselines, lint, housekeeping
- `refactor/` — code restructuring without behavior change
- `test/` — adding or fixing tests only
- `docs/` — docs / comments only
- `style/` — formatting only (no logic)
- `ci/` — CI config (.github/, workflows)
- `build/` — Gradle build files, Kotlin/AGP plugin bumps
- `perf/` — performance work

If the change spans multiple categories, pick the one that best describes the *intent* (read the commit messages to find it). Tie-break toward `feat` > `fix` > `refactor` > `chore`.

## 3. Generate the slug

- 2–5 lowercase words, kebab-case, derived from what the change actually does (not the file paths). Read the commit subjects.
- Drop filler words (`add`, `update`, `the`, `a`).
- Examples: `chore/per-module-detekt-baselines`, `feat/transaction-split-screen`, `fix/ios-offline-bundle-id`.
- Final branch name: `<prefix>/<slug>`.

If the branch is *already* named with a valid prefix and a meaningful slug, skip the rename and tell the user.

## 4. Rename the branch locally

```
git branch -m <new-name>
```

## 5. Handle uncommitted changes

If `git status` shows uncommitted work, propose a commit message (1–2 sentences, imperative, why-not-what, matching the style of recent `main` commits). Confirm with the user only if the diff is large or ambiguous; otherwise commit directly via the standard commit protocol with the `Co-Authored-By: Claude` trailer.

If everything is already committed, skip this step.

## 6. Push and create PR

Run in parallel:

- `git push -u origin <new-name>`
- `gh pr create --title "<title>" --body "$(cat <<'EOF'
## Summary
<bullets>

## Test plan
- [ ] <items>

<Closes #N — include this line when the branch resolves a GitHub issue (the
issue number is usually in the branch history or the task prompt); omit it
otherwise>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"`

PR title rules:
- Under 70 chars.
- Same conventional prefix as the branch, but in commit-message form: `feat: ...`, `chore: ...`.
- Summarize *all* commits on the branch, not only the last one.

## 7. Report back

Print the new branch name and the PR URL. Nothing else.

## Guardrails

- Never force-push.
- Never push to `main`.
- Never run `git reset --hard` or delete branches.
- If `gh pr create` fails because a PR already exists, just print the existing URL (`gh pr view --json url -q .url`) and stop.
