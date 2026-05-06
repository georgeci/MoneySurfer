---
description: Save the current Claude plan to docs/plans/ and add a GitHub issue with Status=Backlog to project #2
argument-hint: "[slug]"
---

You are turning the **current Claude plan** (the one just produced in this conversation, e.g. via plan mode or explicit planning discussion) into two artefacts:

1. A markdown file under `docs/plans/<slug>.md`.
2. A GitHub issue in `georgeci/MoneySurfer`, added to project [github.com/users/georgeci/projects/2](https://github.com/users/georgeci/projects/2) with Status field set to **Backlog**.

If no plan has been produced yet in this conversation, stop and tell the user — do not invent one.

## Constants

- Repo: `georgeci/MoneySurfer`
- Project owner: `georgeci` (user-scoped project)
- Project number: `2`
- Status field value: `Backlog`

## 1. Resolve inputs

- **Slug** — first positional argument if given. Otherwise derive a 2–5 word kebab-case slug from the plan title (drop filler words like `add`, `update`, `the`).
- **Title** — first H1 of the plan, or the first sentence if there is no H1. Under 70 chars.

## 2. Write the plan file

Path: `docs/plans/<slug>.md`. If the file already exists, append a numeric suffix (`-2`, `-3`, …). Create `docs/plans/` if missing.

Frontmatter + body:

```markdown
---
title: <Title>
created: <YYYY-MM-DD>            # today's date from context
status: backlog
---

# <Title>

<full plan body — preserve the original structure, headings, checklists, code blocks>
```

Do not paraphrase the plan. Copy it verbatim into the body.

## 3. Check gh scopes

Run `gh auth status` once. If output does not include the `project` scope, stop and tell the user:

```
gh auth refresh -s project
```

Do not proceed without that scope — `gh project item-edit` will fail otherwise.

## 4. Create the issue

```
gh issue create \
  --repo georgeci/MoneySurfer \
  --title "<Title>" \
  --body  "$(cat <<'EOF'
<short summary — 2–4 bullets pulled from the plan>

---
Plan: docs/plans/<slug>.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Capture the issue URL from stdout.

## 5. Add the issue to project #2 and set Status=Backlog

Resolve project + field IDs once (cache them within the run):

```
PROJECT_ID=$(gh project view 2 --owner georgeci --format json --jq '.id')
STATUS_FIELD_JSON=$(gh project field-list 2 --owner georgeci --format json)
STATUS_FIELD_ID=$(echo "$STATUS_FIELD_JSON" | jq -r '.fields[] | select(.name=="Status") | .id')
BACKLOG_OPTION_ID=$(echo "$STATUS_FIELD_JSON" | jq -r '.fields[] | select(.name=="Status") | .options[] | select(.name=="Backlog") | .id')
```

Add the issue and set status:

```
ITEM_ID=$(gh project item-add 2 --owner georgeci --url <ISSUE_URL> --format json --jq '.id')

gh project item-edit \
  --id "$ITEM_ID" \
  --project-id "$PROJECT_ID" \
  --field-id "$STATUS_FIELD_ID" \
  --single-select-option-id "$BACKLOG_OPTION_ID"
```

If any of `STATUS_FIELD_ID` or `BACKLOG_OPTION_ID` come back empty, print the raw `field-list` JSON to the user and stop — the field/option name probably differs from the assumed `Status` / `Backlog`.

## 6. Report back

Print exactly:

- Plan file path (as a clickable markdown link).
- Issue URL.
- Project URL: https://github.com/users/georgeci/projects/2

Nothing else.

## Guardrails

- Never commit or push the plan file — leave it on disk for the user to review.
- Never edit existing plan files; always create a new one.
- If the issue is created but adding to the project fails, do not delete the issue — print the URL and the error so the user can finish manually.
