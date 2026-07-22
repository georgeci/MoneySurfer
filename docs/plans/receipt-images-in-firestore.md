---
title: Receipt images as base64 in a dedicated Firestore subcollection
created: 2026-07-22
status: backlog
---

# Receipt images as base64 in a dedicated Firestore subcollection

Attach a photo of a receipt to a transaction and replicate it across the
devices of a workspace, **without introducing Firebase Storage**.

Nothing is implemented yet: `grep -ri receipt` only matches UI strings and
icons, and `firebase.json` configures Firestore only.

## Decision

Store the compressed image as a base64 string inside its own Firestore
document at `workspaces/{wid}/receipts/{receiptId}`, synchronised by one new
`SyncEntityPlugin`.

This is the only option that adds **no new subsystem** — everything already
built keeps working as-is.

| Problem with a Firebase Storage design | Status under this design |
|---|---|
| Storage requires the Blaze plan | Not needed — Firestore free tier |
| No `storage.rules`, no Storage emulator, no rules tests | Rules go into the existing `firestore.rules`, tests into `firestore-tests/` |
| Blobs need their own resumable upload queue — the outbox stores no payload, it queues `(entityType, entityId)` and the push worker re-reads the row from Room | The image is a field on the row the push worker already re-reads — plain `enqueueUpsert` |
| Dangling references (doc pushed before the file lands) | Image lives *inside* the document — atomic |
| LWW does not apply to blobs | Applies — ordinary `updatedAt` |
| Soft-delete vs hard-delete, orphaned objects | Tombstones, same as every other entity |
| Account deletion does not purge Storage objects (no Cloud Functions to do it server-side) | Purged by the existing Firestore path — but `ENTITY_COLLECTIONS` in `UserAccountDeletionRepositoryImpl` is a fixed list, so `receipts` must be added to it or the docs are left behind |
| `firebase-storage` dependency + expect/actual `File`/`NSURL` | No new dependency; common code sees `ByteArray`/`String` |
| `firestore.get()` billed on every image fetch in Storage rules | Not applicable |

Implementation is essentially a copy of `SavingsGoalSyncPlugin.kt` with an
`imageBase64: String` field.

## Constraints to accept deliberately

1. **Firestore document limit is 1 MiB.** base64 adds ~33%, so the JPEG must
   be **≤ ~700 KB**. Not a real limit for a receipt: 1280px on the long edge
   at quality 70–75 gives 100–250 KB. Client-side resize is mandatory and the
   cap must be enforced in the rules, type-guarded like every other field:
   `request.resource.data.imageBase64 is string &&
   request.resource.data.imageBase64.size() < 900000`.
2. **Receipts must not ride the ordinary cursor pull** — every sync would drag
   megabytes. Either a dedicated low-priority plugin with lazy fetch (download
   when the transaction is opened) or `pullPriority` at the very end with small
   batches. This is the only part that needs design rather than copy-paste.
3. **A separate document, not a field on the transaction** — otherwise
   `TransactionDoc` bloats and every device pulls it on every sync.
4. **The cost is egress, not storage.** 100 receipts × 200 KB × 3 devices is
   noise; 10 000 receipts is not.

## When Firebase Storage becomes the right call

If the product needs **multiple photos per transaction, PDF statements, or
uncompressed originals**, base64 in Firestore hits the document limit and
Storage should be built instead. Threshold: one compressed photo per
transaction — below it Firestore, above it Storage.

## Rejected alternatives

- **S3 / Cloudinary / Supabase** — client-side keys or a backend to sign URLs,
  plus a second authentication system. Strictly harder than Storage.
- **Local-only, no sync** — one step simpler, but breaks the core scenario: in
  a shared workspace the other member never sees the receipt.

## Implementation outline

- [ ] Room: `receipts` table (id, transactionId, workspaceId, imageBase64 or a
      local file path, `updatedAt`, `deletedAt`, `clientVersionCode`).
- [ ] `RemoteDtos.kt`: `ReceiptDoc` with the wire shape.
- [ ] `SyncCollection.RECEIPTS` + `SyncEntityTypes.RECEIPT` + a pull priority
      after every other entity.
- [ ] `ReceiptSyncPlugin` modelled on `SavingsGoalSyncPlugin`, with lazy pull.
- [ ] `firestore.rules`: membership gate, write-shape validation for
      `ReceiptDoc`, and the size cap.
- [ ] `firestore-tests/`: rules tests for membership, size cap, and write shape.
- [ ] Add `SyncCollection.RECEIPTS` to `ENTITY_COLLECTIONS` in
      `UserAccountDeletionRepositoryImpl` so account deletion purges receipts.
- [ ] Platform image capture (Android Photo Picker, iOS PHPicker) behind a
      common `expect` API, plus EXIF orientation handling and HEIC → JPEG.
- [ ] Client-side downscale + compression to the agreed budget.
- [ ] UI: attach / view / remove a receipt on the transaction screen, with a
      state for "not downloaded yet".
