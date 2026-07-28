const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
} = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  unauthed,
  withAdmin,
  CLIENT_VERSION,
} = require('./helpers');

// UserConfigDoc — data-remote/.../RemoteDtos.kt. The client writes all three fields
// (gitlive encodes defaults), which is why the rule may require them rather than
// tolerating a minimal document the way the workspace entity shapes do.
function configDoc(overrides = {}) {
  return {
    value: 'Dark',
    updatedAt: 1700000000000,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

const KEY = 'ui.theme_mode';

describe('users/{uid}/config/{key}', () => {
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
      await setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc());
    });
  });

  // ── reads ──────────────────────────────────────────────────────────────────

  it('a user can read one of their own settings', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(getDoc(doc(db, `users/alice-uid/config/${KEY}`)));
  });

  it('a user can list their whole settings collection', async () => {
    // The user-scoped pull phase has no cursor: it reads the collection whole, so a bare
    // `list` has to be allowed — a filtered, ordered query is not what the client sends.
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(getDocs(collection(db, 'users/alice-uid/config')));
  });

  it('a user cannot read another user\'s settings', async () => {
    const db = authedAs(env, 'bob-uid', { email: 'bob@example.com' });
    await assertFails(getDoc(doc(db, `users/alice-uid/config/${KEY}`)));
  });

  it('a user cannot list another user\'s settings', async () => {
    const db = authedAs(env, 'bob-uid', { email: 'bob@example.com' });
    await assertFails(getDocs(collection(db, 'users/alice-uid/config')));
  });

  it('an unauthenticated client is denied', async () => {
    const db = unauthed(env);
    await assertFails(getDoc(doc(db, `users/alice-uid/config/${KEY}`)));
    await assertFails(getDocs(collection(db, 'users/alice-uid/config')));
  });

  // ── writes ─────────────────────────────────────────────────────────────────

  it('a user can create a setting of their own', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(
      setDoc(doc(db, 'users/alice-uid/config/ui.container_style'), configDoc({ value: 'Flat' })),
    );
  });

  it('a user can update a setting of their own', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(
      setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc({ value: 'Light' })),
    );
  });

  it('a user cannot write into another user\'s settings', async () => {
    const db = authedAs(env, 'attacker-uid', { email: 'attacker@example.com' });
    await assertFails(
      setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc({ value: 'Light' })),
    );
  });

  it('an unauthenticated client cannot write', async () => {
    const db = unauthed(env);
    await assertFails(setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc()));
  });

  // ── write shape (issue #156 poison-document guard) ────────────────────────
  //
  // These documents are client-written and the pull path reads `updatedAt`, so a
  // malformed value is exactly the poison document the guard exists to reject.

  it('rejects a non-string value', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertFails(
      setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc({ value: 42 })),
    );
  });

  it('rejects a missing value', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    const { value, ...withoutValue } = configDoc();
    await assertFails(setDoc(doc(db, `users/alice-uid/config/${KEY}`), withoutValue));
  });

  it('rejects a value over 1024 characters', async () => {
    // The size cap is what stops a self-writable collection becoming free storage.
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertFails(
      setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc({ value: 'x'.repeat(1025) })),
    );
  });

  it('accepts a value at exactly 1024 characters', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(
      setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc({ value: 'x'.repeat(1024) })),
    );
  });

  it('rejects a non-integer updatedAt', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertFails(
      setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc({ updatedAt: 'yesterday' })),
    );
  });

  it('rejects a missing clientVersionCode', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    const { clientVersionCode, ...withoutVersion } = configDoc();
    await assertFails(setDoc(doc(db, `users/alice-uid/config/${KEY}`), withoutVersion));
  });

  it('rejects an unknown field', async () => {
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertFails(
      setDoc(doc(db, `users/alice-uid/config/${KEY}`), configDoc({ deviceId: 'pixel-4a' })),
    );
  });

  it('rejects a malformed update of an existing document', async () => {
    // Update goes through the same shape check as create — a client cannot degrade a
    // valid document field by field.
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertFails(updateDoc(doc(db, `users/alice-uid/config/${KEY}`), { value: 42 }));
  });

  // ── delete (the account-deletion purge) ───────────────────────────────────

  it('a user can delete their own setting', async () => {
    // Firestore does not cascade deletes into subcollections, so the deletion flow clears
    // this collection before `users/{uid}` — otherwise the data is orphaned permanently.
    const db = authedAs(env, 'alice-uid', { email: 'alice@example.com' });
    await assertSucceeds(deleteDoc(doc(db, `users/alice-uid/config/${KEY}`)));
  });

  it('a user cannot delete another user\'s setting', async () => {
    const db = authedAs(env, 'bob-uid', { email: 'bob@example.com' });
    await assertFails(deleteDoc(doc(db, `users/alice-uid/config/${KEY}`)));
  });
});
