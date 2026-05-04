const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
} = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  unauthed,
  withAdmin,
} = require('./helpers');

describe('userEmails/{email}', () => {
  let env;

  before(async () => {
    env = await getTestEnv();
  });

  after(async () => {
    await shutdownTestEnv();
  });

  beforeEach(async () => {
    await resetTestEnv();
  });

  // ── reads ────────────────────────────────────────────────────────────────

  it('signed-in user can read a userEmails doc by exact key', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, 'userEmails/known@example.com'), {
        uid: 'known-uid',
        updatedAt: 0,
      });
    });
    const db = authedAs(env, 'reader-uid', { email: 'reader@example.com' });
    await assertSucceeds(getDoc(doc(db, 'userEmails/known@example.com')));
  });

  it('unauthenticated user cannot read userEmails (auth required)', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, 'userEmails/known@example.com'), {
        uid: 'known-uid',
        updatedAt: 0,
      });
    });
    const db = unauthed(env);
    await assertFails(getDoc(doc(db, 'userEmails/known@example.com')));
  });

  it('list/scan is denied — prevents email enumeration', async () => {
    const db = authedAs(env, 'attacker-uid', { email: 'attacker@example.com' });
    await assertFails(getDocs(collection(db, 'userEmails')));
  });

  // ── writes ──────────────────────────────────────────────────────────────

  it('user can write their own email mapping', async () => {
    const email = 'self@example.com';
    const db = authedAs(env, 'self-uid', { email });
    await assertSucceeds(
      setDoc(doc(db, `userEmails/${email}`), { uid: 'self-uid', updatedAt: 0 }),
    );
  });

  it('user cannot squat on someone else\'s email key (mapping to own uid)', async () => {
    // The squatting attack the rule update was meant to close: pre-write
    // `userEmails/victim@example.com → my_uid` to steal incoming invites.
    const db = authedAs(env, 'attacker-uid', { email: 'attacker@example.com' });
    await assertFails(
      setDoc(
        doc(db, 'userEmails/victim@example.com'),
        { uid: 'attacker-uid', updatedAt: 0 },
      ),
    );
  });

  it('user cannot map a foreign uid to their own email', async () => {
    const email = 'self@example.com';
    const db = authedAs(env, 'self-uid', { email });
    await assertFails(
      setDoc(doc(db, `userEmails/${email}`), { uid: 'someone-else-uid', updatedAt: 0 }),
    );
  });

  it('user without an email claim (e.g. anonymous) cannot write', async () => {
    // The rule requires `request.auth.token.email is string` — fails closed for
    // anonymous accounts and providers that don't surface an email claim.
    const db = authedAs(env, 'anon-uid');
    await assertFails(
      setDoc(
        doc(db, 'userEmails/anything@example.com'),
        { uid: 'anon-uid', updatedAt: 0 },
      ),
    );
  });

  it('case mismatch between email claim and key is rejected', async () => {
    // The mapping convention is lowercase; the rule compares with `.lower()`.
    // A key with mixed case that doesn't match the lowercased claim is denied.
    const db = authedAs(env, 'self-uid', { email: 'self@example.com' });
    await assertFails(
      setDoc(
        doc(db, 'userEmails/Self@Example.com'),
        { uid: 'self-uid', updatedAt: 0 },
      ),
    );
  });

  it('hard-delete is denied', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, 'userEmails/known@example.com'), {
        uid: 'known-uid',
        updatedAt: 0,
      });
    });
    const db = authedAs(env, 'known-uid', { email: 'known@example.com' });
    await assertFails(deleteDoc(doc(db, 'userEmails/known@example.com')));
  });
});
