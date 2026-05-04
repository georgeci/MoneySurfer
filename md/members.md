# Members, Roles, Invites — Forward Plan (Phases 4–5)

As-built reference (model, repo, sync, rules) is promoted to [docs/features/members-and-invites.md](../docs/features/members-and-invites.md).

This file keeps only the still-pending UI/uikit work. Phases 0–3 (domain, Room, sync, Firestore rules + indexes, integration tests) shipped — see the docs version.

## Decisions (locked)

| # | Question | Decision |
|---|---|---|
| 1 | Role naming | **Keep `OWNER / EDITOR / VIEWER`** |
| 2 | Member status set | **`ACTIVE / LEFT / REMOVED`** only (no `INVITED` — pending state lives in `WorkspaceInvite`) |
| 3 | Invite-link tokens | **Dropped.** No tokens, no `WorkspaceInviteLink` |
| 4 | Email delivery | **Dropped.** In-app only — invite written to Firestore, surfaced when invitee opens app |
| 5 | Owner transfer | **Deferred** to post-v1 (`TransferOwnershipUseCase`) |

## Phase 4 — Shared (feature) layer

### Members screen — wire real data

- `shared/.../workspace/members/WorkspaceMembersViewModel.kt`:
  - Replace `PLACEHOLDER_MEMBERS` with real `combine(memberRepo.getByWorkspaceId(currentWid), inviteRepo.getByWorkspaceId(currentWid))`.
  - Filter members `status == ACTIVE` for main list; pending invites in their own section.
  - State: `Content(members: List<MemberUi>, pendingInvites: List<InviteUi>, canManage: Boolean)` (`canManage` = current user role == OWNER).
  - Events: `OnInviteClick`, `OnMemberClick(uid)`, `OnInviteCancel(id)`, `OnInviteResend(id)` (re-upsert with bumped expiresAt), `OnLeave`.
  - Effects: `OpenInviteSheet`, `OpenMemberActions(uid)`, `Dismiss`, `ShowError(msg)`.

### New screens

- `shared/.../workspace/invite/WorkspaceInviteScreen.kt` + `WorkspaceInviteViewModel.kt` — bottom-sheet form: email field, role picker (OWNER hidden), submit → `SendInviteUseCase`. Loading/error/success effects.
- `shared/.../workspace/invite/PendingInvitesScreen.kt` + VM — current-user inbox: pending invites for `email` / `targetUserId == me`. Accept/Decline buttons.
- `shared/.../workspace/members/MemberActionsBottomSheet.kt` — change role / remove / leave.

### App entry surfacing (in-app only — decision 4)

On app launch, after auth: query `ListPendingInvitesForCurrentUserUseCase`. Surface as banner on home screen ("You have N pending invites → tap to view") + badge in workspace switcher. No push, no email.

### Navigation

- Register new routes in the existing nav graph.
- Wire `OnInviteClick` (currently no-op in `WorkspaceMembersViewModel`) to `OpenInviteSheet` effect.

### MVI

Follow existing `MviViewModel<State, Event, Effect>` pattern. Use `@KoinViewModel`.

### Tests

VM tests mirror conventions from `domain/src/commonTest/.../usecase/`.

## Phase 5 — UIKit components

Add under `uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/`:

- `SurferInvitePendingRow.kt` — email, role chip, status badge (PENDING/EXPIRED), trailing overflow menu (Resend/Cancel for owner; Accept/Decline for invitee). Reuse hue palette from `SurferMemberRow`.
- `SurferInviteForm.kt` — `OutlinedTextField` (email) + `SurferDropdown` (role) + submit button.
- `SurferRoleMenu.kt` — extracted dropdown for role selection used by both form and member-actions sheet.
- Update `uikit/.../SurferMemberRow.kt`: add optional `onClick`, `trailingMenu` slot. Keep backwards-compatible: new params default null.

Strings (shared `composeResources`) — add invite/member labels, role labels, status badges.

## Out of scope (post-v1)

- `TransferOwnershipUseCase` — needs atomic double-write; without Cloud Functions, sequential updates have race window. Add when first user hits `OwnerCannotLeave`.
- Email delivery — invites visible in-app only.
- Invite-link tokens — schema doesn't carry token columns; additive migration if revisited.
- Cloud Functions for `EXPIRED` status flip — client computes display-only; persisted PENDING stays PENDING until cancelled.
