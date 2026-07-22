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

// GoalDoc — mirrors data-remote/.../RemoteDtos.kt
function goalDoc(overrides = {}) {
  return {
    title: 'New bike',
    emoji: '🚲',
    hue: 210,
    target: 150000,
    currencyCode: 'USD',
    startDate: '2026-07-01',
    targetDate: '2026-12-31',
    accountId: null,
    status: 'ACTIVE',
    createdAt: 1,
    updatedAt: 1,
    deletedAt: null,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

// GoalContributionDoc — mirrors data-remote/.../RemoteDtos.kt
function contributionDoc(overrides = {}) {
  return {
    goalId: 'goal-1',
    amount: 5000,
    occurredOn: '2026-07-10',
    note: 'Payday',
    transferId: null,
    createdAt: 1,
    updatedAt: 1,
    deletedAt: null,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

describe('savings goals (issue #244)', () => {
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
      await setDoc(doc(db, `workspaces/${WID}/goals/goal-1`), goalDoc());
      await setDoc(
        doc(db, `workspaces/${WID}/goalContributions/gc-1`),
        contributionDoc(),
      );
    });
  });

  const asMember = () => authedAs(env, MEMBER, { email: 'member@example.com' });
  const asStranger = () => authedAs(env, STRANGER, { email: 'stranger@example.com' });
  const asOwner = () => authedAs(env, OWNER, { email: 'owner@example.com' });

  // ── membership gate ────────────────────────────────────────────────────────

  it('member can read and create a goal', async () => {
    await assertSucceeds(getDoc(doc(asMember(), `workspaces/${WID}/goals/goal-1`)));
    await assertSucceeds(
      setDoc(doc(asMember(), `workspaces/${WID}/goals/goal-2`), goalDoc()),
    );
  });

  it('non-member cannot read or create a goal', async () => {
    await assertFails(getDoc(doc(asStranger(), `workspaces/${WID}/goals/goal-1`)));
    await assertFails(
      setDoc(doc(asStranger(), `workspaces/${WID}/goals/goal-3`), goalDoc()),
    );
  });

  it('member can read and create a contribution', async () => {
    await assertSucceeds(
      getDoc(doc(asMember(), `workspaces/${WID}/goalContributions/gc-1`)),
    );
    await assertSucceeds(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goalContributions/gc-2`),
        contributionDoc(),
      ),
    );
  });

  it('non-member cannot read or create a contribution', async () => {
    await assertFails(
      getDoc(doc(asStranger(), `workspaces/${WID}/goalContributions/gc-1`)),
    );
    await assertFails(
      setDoc(
        doc(asStranger(), `workspaces/${WID}/goalContributions/gc-3`),
        contributionDoc(),
      ),
    );
  });

  it('an evicted (REMOVED) member loses access to goals', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'REMOVED' }),
      );
    });
    await assertFails(getDoc(doc(asMember(), `workspaces/${WID}/goals/goal-1`)));
  });

  // ── client-version floor ───────────────────────────────────────────────────

  it('goal create is blocked when clientVersionCode is missing', async () => {
    const dto = goalDoc();
    delete dto.clientVersionCode;
    await assertFails(
      setDoc(doc(asMember(), `workspaces/${WID}/goals/goal-nover`), dto),
    );
  });

  it('contribution create is blocked when clientVersionCode is below the floor', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goalContributions/gc-oldver`),
        contributionDoc({ clientVersionCode: 0 }),
      ),
    );
  });

  // ── soft-delete / hard-delete ──────────────────────────────────────────────

  it('member can tombstone a goal via update', async () => {
    await assertSucceeds(
      updateDoc(doc(asMember(), `workspaces/${WID}/goals/goal-1`), {
        deletedAt: 5,
        updatedAt: 5,
        clientVersionCode: CLIENT_VERSION,
      }),
    );
  });

  it('owner can hard-delete a goal and a contribution (purge path)', async () => {
    await assertSucceeds(deleteDoc(doc(asOwner(), `workspaces/${WID}/goals/goal-1`)));
    await assertSucceeds(
      deleteDoc(doc(asOwner(), `workspaces/${WID}/goalContributions/gc-1`)),
    );
  });

  it('hard-delete is denied for non-owner members', async () => {
    await assertFails(deleteDoc(doc(asMember(), `workspaces/${WID}/goals/goal-1`)));
    await assertFails(
      deleteDoc(doc(asMember(), `workspaces/${WID}/goalContributions/gc-1`)),
    );
  });

  // ── write-shape validation (issue #156 contract) ───────────────────────────
  // A field is type-checked only WHEN PRESENT, so a minimal doc must still pass.

  it('accepts a minimal goal with default-valued fields omitted', async () => {
    await assertSucceeds(
      setDoc(doc(asMember(), `workspaces/${WID}/goals/goal-min`), {
        title: 'Sparse',
        updatedAt: 1,
        clientVersionCode: CLIENT_VERSION,
      }),
    );
  });

  it('rejects a goal whose numeric target is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goals/goal-bad`),
        goalDoc({ target: 'lots' }),
      ),
    );
  });

  it('rejects a goal whose hue is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goals/goal-bad2`),
        goalDoc({ hue: 'blue' }),
      ),
    );
  });

  it('rejects a goal whose title is a number', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goals/goal-bad3`),
        goalDoc({ title: 42 }),
      ),
    );
  });

  it('accepts a goal with nullable targetDate/accountId set to null', async () => {
    await assertSucceeds(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goals/goal-nulls`),
        goalDoc({ targetDate: null, accountId: null }),
      ),
    );
  });

  it('rejects a goal whose nullable accountId has a wrong non-null type', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goals/goal-badacc`),
        goalDoc({ accountId: 7 }),
      ),
    );
  });

  it('rejects a poison update of an existing goal (target as string)', async () => {
    await assertFails(
      updateDoc(doc(asMember(), `workspaces/${WID}/goals/goal-1`), {
        target: 'abc',
        updatedAt: 2,
        clientVersionCode: CLIENT_VERSION,
      }),
    );
  });

  it('accepts a negative contribution amount (a withdrawal)', async () => {
    await assertSucceeds(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goalContributions/gc-withdrawal`),
        contributionDoc({ amount: -2500 }),
      ),
    );
  });

  it('rejects a contribution whose amount is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goalContributions/gc-poison`),
        contributionDoc({ amount: 'abc' }),
      ),
    );
  });

  it('rejects a contribution whose goalId is a number', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goalContributions/gc-badgoal`),
        contributionDoc({ goalId: 1 }),
      ),
    );
  });

  it('rejects a contribution whose nullable transferId has a wrong non-null type', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/goalContributions/gc-badtransfer`),
        contributionDoc({ transferId: 9 }),
      ),
    );
  });
});
