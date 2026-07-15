const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc } = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  withAdmin,
  workspaceDoc,
  memberDoc,
  inviteDoc,
  CLIENT_VERSION,
} = require('./helpers');

const WID = 'ws-1';
const OWNER = 'owner-uid';
const MEMBER = 'member-uid';
const STRANGER = 'stranger-uid';

// The push-side soft-delete shape: every *SyncPlugin sends this exact
// field-mask update for MutationOperation.DELETE (TombstonePush.kt).
// Hard deletes are denied collection-wide, so this update is the ONLY
// delete shape clients can produce — these tests pin that contract.
function tombstonePatch(overrides = {}) {
  return {
    deletedAt: 1700000111222,
    updatedAt: 1700000111222,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

function entityDoc(overrides = {}) {
  return {
    name: 'Doomed entity',
    createdAt: 0,
    updatedAt: 0,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

describe('tombstone update shape (push-side soft delete)', () => {
  let env;

  before(async () => {
    env = await getTestEnv();
  });

  after(async () => {
    await shutdownTestEnv();
  });

  beforeEach(async () => {
    await resetTestEnv();
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, `workspaces/${WID}`), workspaceDoc({ ownerId: OWNER }));
      await setDoc(doc(db, `workspaces/${WID}/members/${OWNER}`), memberDoc({ role: 'OWNER' }));
      await setDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), memberDoc({ role: 'EDITOR' }));
      await setDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), entityDoc());
      await setDoc(doc(db, `workspaces/${WID}/invites/inv-1`), inviteDoc());
    });
  });

  // ── entity collections (accounts exhaustive, rest smoke) ─────────────────

  it('member can tombstone an account', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), tombstonePatch()),
    );
  });

  it('non-member cannot tombstone an account', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), tombstonePatch()),
    );
  });

  it('tombstone with clientVersionCode below floor is denied', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(
        doc(db, `workspaces/${WID}/accounts/acc-1`),
        tombstonePatch({ clientVersionCode: 0 }),
      ),
    );
  });

  for (const collection of ['categories', 'transactions']) {
    it(`member can tombstone in ${collection}`, async () => {
      await withAdmin(env, async (db) => {
        await setDoc(doc(db, `workspaces/${WID}/${collection}/entry-1`), entityDoc());
      });
      const db = authedAs(env, MEMBER, { email: 'member@example.com' });
      await assertSucceeds(
        updateDoc(doc(db, `workspaces/${WID}/${collection}/entry-1`), tombstonePatch()),
      );
    });
  }

  // ── workspace root doc ────────────────────────────────────────────────────

  it('owner can tombstone the workspace root doc', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}`), tombstonePatch()),
    );
  });

  it('non-owner member cannot tombstone the workspace root doc', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}`), tombstonePatch()),
    );
  });

  // ── members ───────────────────────────────────────────────────────────────

  it('owner can tombstone a member row', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), tombstonePatch()),
    );
  });

  it('member can tombstone their own row (self-leave: role and status unchanged)', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), tombstonePatch()),
    );
  });

  it("member cannot tombstone another member's row", async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/members/${OWNER}`), tombstonePatch()),
    );
  });

  // ── invites ───────────────────────────────────────────────────────────────

  it('owner can tombstone an invite', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), tombstonePatch()),
    );
  });

  it('invitee cannot tombstone a PENDING invite (known limitation: only status transitions)', async () => {
    // Documents the accepted gap: an invitee-originated DELETE of a PENDING
    // invite is rejected (the update branch only allows PENDING→ACCEPTED/DECLINED
    // for invitees). App flows route invite decline/revoke through status
    // updates, so no client produces this shape today.
    const db = authedAs(env, 'invitee-uid', { email: 'invitee@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), tombstonePatch()),
    );
  });
});
