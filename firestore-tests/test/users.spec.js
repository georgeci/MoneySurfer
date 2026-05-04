const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { arrayUnion, doc, getDoc, setDoc, updateDoc } = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  unauthed,
  withAdmin,
} = require('./helpers');

function userDoc(overrides = {}) {
  return {
    displayName: 'Alice',
    email: 'alice@example.com',
    isAnon: false,
    createdAt: 0,
    workspaceIds: [],
    defaultWorkspaceId: null,
    invitedWorkspaceIds: [],
    ...overrides,
  };
}

describe('users/{uid}', () => {
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
      await setDoc(doc(db, 'users/alice-uid'), userDoc());
    });
  });

  it('user can read their own document', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(getDoc(doc(db, 'users/alice-uid')));
  });

  it('user cannot read another user\'s document', async () => {
    const db = authedAs(env, 'bob-uid', { email: 'bob@example.com' });
    await assertFails(getDoc(doc(db, 'users/alice-uid')));
  });

  it('unauthenticated user is denied', async () => {
    const db = unauthed(env);
    await assertFails(getDoc(doc(db, 'users/alice-uid')));
  });

  it('user can create their own document', async () => {
    const db = authedAs(env, 'carol-uid', { email: 'carol@example.com' });
    await assertSucceeds(
      setDoc(doc(db, 'users/carol-uid'), userDoc({ email: 'carol@example.com' })),
    );
  });

  it('user cannot create a document under another uid', async () => {
    const db = authedAs(env, 'attacker-uid', { email: 'attacker@example.com' });
    await assertFails(
      setDoc(doc(db, 'users/victim-uid'), userDoc({ email: 'victim@example.com' })),
    );
  });

  it('user can update their own document', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, 'users/alice-uid'), { displayName: 'Alice the Magnificent' }),
    );
  });

  it('user cannot update another user\'s document', async () => {
    const db = authedAs(env, 'bob-uid', { email: 'bob@example.com' });
    await assertFails(
      updateDoc(doc(db, 'users/alice-uid'), { displayName: 'pwned' }),
    );
  });
});

// ─── invitedWorkspaceIds — constrained cross-user write ───────────────────────
//
// When a workspace owner sends an invite they write the workspace ID into the
// recipient's `invitedWorkspaceIds` array so the recipient's next syncAll() can
// discover the invite without a collectionGroup query.
//
// The rule deliberately narrows what a cross-user update may do:
//  - only `invitedWorkspaceIds` may change,
//  - only one entry may be added per call (prevents bulk-flood),
//  - existing entries may not be removed (only the owner can shrink),
//  - total size capped at 100.

describe('users/{uid} — invitedWorkspaceIds cross-user append', () => {
  let env;

  before(async () => { env = await getTestEnv(); });
  after(async () => { await shutdownTestEnv(); });

  beforeEach(async () => {
    await resetTestEnv();
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, 'users/alice-uid'), userDoc({ invitedWorkspaceIds: [] }));
    });
  });

  it('invite sender can append one wid to recipient invitedWorkspaceIds', async () => {
    const db = authedAs(env, 'sender-uid', { email: 'sender@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, 'users/alice-uid'), {
        invitedWorkspaceIds: arrayUnion('ws-new'),
      }),
    );
  });

  it('unauthenticated user cannot append to invitedWorkspaceIds', async () => {
    const db = unauthed(env);
    await assertFails(
      updateDoc(doc(db, 'users/alice-uid'), {
        invitedWorkspaceIds: arrayUnion('ws-new'),
      }),
    );
  });

  it('sender cannot touch any other field alongside invitedWorkspaceIds', async () => {
    const db = authedAs(env, 'sender-uid', { email: 'sender@example.com' });
    await assertFails(
      updateDoc(doc(db, 'users/alice-uid'), {
        invitedWorkspaceIds: arrayUnion('ws-new'),
        displayName: 'hacked',
      }),
    );
  });

  it('sender cannot remove entries from invitedWorkspaceIds', async () => {
    // Pre-seed a non-empty list, then attempt to replace it with a shorter one.
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, 'users/alice-uid'), userDoc({
        invitedWorkspaceIds: ['ws-existing'],
      }));
    });
    // arrayRemove is equivalent to replacing the array with a smaller subset;
    // the hasAll guard in the rule catches any removal.
    const { arrayRemove } = require('firebase/firestore');
    const db = authedAs(env, 'sender-uid', { email: 'sender@example.com' });
    await assertFails(
      updateDoc(doc(db, 'users/alice-uid'), {
        invitedWorkspaceIds: arrayRemove('ws-existing'),
      }),
    );
  });

  it('owner can remove entries from their own invitedWorkspaceIds', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, 'users/alice-uid'), userDoc({
        invitedWorkspaceIds: ['ws-old'],
      }));
    });
    const { arrayRemove } = require('firebase/firestore');
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, 'users/alice-uid'), {
        invitedWorkspaceIds: arrayRemove('ws-old'),
      }),
    );
  });

  it('sender cannot append when list is already at cap (100)', async () => {
    const full = Array.from({ length: 100 }, (_, i) => `ws-${i}`);
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, 'users/alice-uid'), userDoc({ invitedWorkspaceIds: full }));
    });
    const db = authedAs(env, 'sender-uid', { email: 'sender@example.com' });
    await assertFails(
      updateDoc(doc(db, 'users/alice-uid'), {
        invitedWorkspaceIds: arrayUnion('ws-overflow'),
      }),
    );
  });
});
