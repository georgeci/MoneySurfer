# Maestro E2E flows

End-to-end flows for the Android and iOS apps, driven by [Maestro](https://maestro.mobile.dev).
Gradle runs the whole directory at once — see `gradle/qa.gradle.kts` (`qaMaestroAndroid`,
`qaMaestroIos`, `qaMaestroIosOffline`).

```bash
./gradlew qaMaestroAndroid
```

A single flow, for local debugging:

```bash
./gradlew maestroRunOne -PmaestroFlow=08_transaction_details_edit_delete.yaml
```

## The numeric prefixes do not define execution order

`00_`, `03a_`, `18_` read like a schedule. They are not one. Gradle invokes
`maestro test scripts/maestro/`, and Maestro discovers flows itself — on the run that
prompted issue #219 it executed `04` before `03a`. Two flows even share the `18_` prefix.
Treat the numbers as nothing more than a rough reading order.

**Order comes from `runFlow` chains only.** A flow that needs a fixture chains the flow that
creates it:

```
00_login → 02_workspace_create → 03_dashboard_add_account → 04_add_transaction
```

Anything a flow does not chain, it must not depend on.

## Rules for writing a flow

**1. Prepare your own entry state; never clean up for whoever runs next.**
A tail that restores state only runs when the flow passes. A flow that fails mid-way skips it,
and the damage lands on unrelated flows — the signature failure mode behind #297 and #351.
`02_workspace_create.yaml` is the model: it *re-selects* `Test Workspace` on the way in,
whatever the previous flow left active, so `03a_dashboard_empty_state.yaml` is free to end
inside `Empty Workspace`.

The exception is a setting the flow itself toggles and the suite has no other opinion about —
`13_appearance_theme_switch.yaml` restores `Follow system` because it is the one that moved it.

**2. Own what you mutate.**
Read-only use of a shared fixture (`Main Bank`, `Test expense`) is fine. Renaming, deleting, or
otherwise mutating one is not: `08` and `17` each create their own transaction
(`Editable expense`, `Undoable expense`) precisely because they destroy it.

**3. Make every write idempotent, including follow-up writes.**
Wrap creation in `runFlow: { when: { notVisible: "<fixture>" } }`. Steps that build *on* a
guarded creation — funding a goal, contributing to a budget — belong inside the same guard, or
they re-apply on every run. `18_goal_create_and_contribute.yaml` used to add another 250 per run
against an assertion hard-coded to 10%.

**4. A tag resolving is not the same as the node being composed.**
Some affordances exist only in one state. The dashboard's add-account CTA
(`dashboard:addAccount`) is composed *only while the account list is empty* — once an account
exists, new ones are created from Accounts → Manage (`dashboard:accountsManage` →
`accountsManage:add`). `10` and `15` both tapped the empty-state CTA after chaining a flow that
guarantees an account, so `15` failed on every run and `10` failed whenever it was scheduled
after `03`. Check the composable, not just the tag name.

Related: a `when: notVisible: "<fixture>"` guard tests what is **on screen**, not what exists.
Scroll the owning widget into view first (`scrollUntilVisible` on `dashboard:recent`), or the
guard will re-create a fixture that is merely below the fold.

**5. Assert what you did, not what it computes to.**
Prefer the recorded contribution (`.*250.*`) over a derived ring percentage; prefer
`assertNotVisible` on your own fixture over "the list is empty", which any neighbouring flow can
falsify (see the Income filter in `07_account_details_and_filters.yaml`).

## Selectors

Target Compose `testTag` values via `id:`. Tags are declared in each screen file as
`object <Screen>TestTags` and exposed to Maestro by `Modifier.surferTestTagAsId()` on the screen
root (Android only — it is a no-op on iOS, where Maestro matches accessibility labels).

Use `text:` in two cases only:

- **fixture data** you yourself typed in — workspace, account, transaction, goal names;
- **copy that is the behaviour under test** — validation messages such as
  `"That doesn't look like a valid email."`

Everything else — screen titles, buttons, rows, section headers — goes through `id:`, so a copy
edit or a locale change cannot turn into a mystery E2E failure (#352). Note that amounts and
dates are locale-formatted: match them with a wrapped regex (`.*150[.,]00.*`), since Maestro's
iOS driver tests the pattern against the whole element label rather than as a substring.

Four copy selectors survive on purpose, each for want of a tag on the node:

| Selector | Flow | Why |
| --- | --- | --- |
| `Undo` | `17` | Snackbar action, composed at the app root rather than by a screen. |
| `All categories` | `18_budget` | Default category scope; the only assertion that the default holds. |
| `Sync`, `Force sync now` | `14` | Flow is excluded from the suite until the sync flag flips (#110). |

Adding a tag for any of them is a welcome follow-up. Everything else that still reads `text:`
is fixture data or a validation message, by the rule above.

## Tags

`tags:` in a flow's header exclude it from the default suite (`maestroSetupTags` in
`gradle/qa.gradle.kts`):

- `setup` — reusable fragment, not a standalone flow (`00_login.yaml`).
- `sync` — needs the sync feature flag on; `14_force_sync_now.yaml` cannot pass in the shipped
  online build (see #110).
- `offline` — the offline-build suite under `offline/`.
