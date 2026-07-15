const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { deleteDoc, doc, getDoc, setDoc, updateDoc } = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  withAdmin,
  workspaceDoc,
  memberDoc,
  CLIENT_VERSION,
} = require('./helpers');

const WID = 'ws-1';
const OWNER = 'owner-uid';
const MEMBER = 'member-uid';
const STRANGER = 'stranger-uid';

// Account/category/transaction/budget/recurringRule rules are structurally identical:
// `read/create/update: if isMember(wid)` plus `hasValidClientVersion()` on writes.
// We exhaustively test ONE collection (`accounts`) and smoke-test the others to
// catch a missed wiring without N× redundancy.

function entityDoc(overrides = {}) {
  return {
    name: 'Test entity',
    createdAt: 0,
    updatedAt: 0,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

describe('member-gated entities (accounts)', () => {
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
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR' }),
      );
      await setDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), entityDoc());
    });
  });

  it('member can read', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  it('non-member cannot read', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  it('member can create', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(
      setDoc(doc(db, `workspaces/${WID}/accounts/acc-2`), entityDoc()),
    );
  });

  it('non-member cannot create', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(
      setDoc(doc(db, `workspaces/${WID}/accounts/acc-3`), entityDoc()),
    );
  });

  it('create blocked when clientVersionCode missing', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    const dto = entityDoc();
    delete dto.clientVersionCode;
    await assertFails(
      setDoc(doc(db, `workspaces/${WID}/accounts/acc-4`), dto),
    );
  });

  it('create blocked when clientVersionCode is below floor (0)', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/accounts/acc-5`),
        entityDoc({ clientVersionCode: 0 }),
      ),
    );
  });

  it('member can update', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), {
        name: 'Renamed',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('hard-delete is denied even for members', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(deleteDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  // ── membership is ACTIVE-only: eviction/leave revokes data access ──────────
  // Member rows are soft-deleted, so isMember must check status — otherwise a
  // REMOVED/LEFT user keeps full read/write to the household's data (issue #152).

  it('an evicted (REMOVED) member can no longer read', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'REMOVED' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  it('an evicted (REMOVED) member can no longer write', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'REMOVED' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), {
        name: 'Renamed',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('a departed (LEFT) member can no longer read', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'LEFT' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });
});

describe('member-gated entities (smoke for the rest)', () => {
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
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR' }),
      );
    });
  });

  for (const collection of ['categories', 'transactions', 'budgets', 'recurringRules']) {
    it(`member can create + read in ${collection}`, async () => {
      const db = authedAs(env, MEMBER, { email: 'member@example.com' });
      const path = `workspaces/${WID}/${collection}/entry-1`;
      await assertSucceeds(setDoc(doc(db, path), entityDoc()));
      await assertSucceeds(getDoc(doc(db, path)));
    });

    it(`non-member is blocked from ${collection}`, async () => {
      const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
      const path = `workspaces/${WID}/${collection}/entry-2`;
      await assertFails(setDoc(doc(db, path), entityDoc()));
    });
  }
});
