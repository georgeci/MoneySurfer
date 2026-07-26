const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { collection, doc, getDoc, getDocs, setDoc } = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  unauthed,
  withAdmin,
} = require('./helpers');

describe('appConfig/{docId}', () => {
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
      await setDoc(doc(db, 'appConfig/mobile'), {
        minSupportedAppVersionCode: 1,
        latestAppVersionCode: 5,
      });
      // Free-form server-owned flags behind the configuration engine's RemoteGlobal layer.
      // Key names are arbitrary; only `remoteOverridable` ones are honoured client-side.
      await setDoc(doc(db, 'appConfig/flags'), {
        'sync.remote_enabled': false,
      });
    });
  });

  // ── reads: single-document get ───────────────────────────────────────────

  it('unauthenticated user can read app config (force-update gate)', async () => {
    // Pre-auth force-update screens need to fetch this without a session.
    const db = unauthed(env);
    await assertSucceeds(getDoc(doc(db, 'appConfig/mobile')));
  });

  it('authenticated user can read app config', async () => {
    const db = authedAs(env, 'reader-uid', { email: 'reader@example.com' });
    await assertSucceeds(getDoc(doc(db, 'appConfig/mobile')));
  });

  it('unauthenticated user can read the flag document', async () => {
    // The RemoteGlobal layer refreshes on launch, which happens before sign-in.
    const db = unauthed(env);
    await assertSucceeds(getDoc(doc(db, 'appConfig/flags')));
  });

  it('authenticated user can read the flag document', async () => {
    const db = authedAs(env, 'reader-uid', { email: 'reader@example.com' });
    await assertSucceeds(getDoc(doc(db, 'appConfig/flags')));
  });

  // ── reads: collection scan ───────────────────────────────────────────────

  it('unauthenticated list/scan is denied — prevents enumerating the collection', async () => {
    // Both readers fetch by exact document id, so denying `list` costs nothing and stops an
    // anonymous scan from discovering every flag document the project has.
    const db = unauthed(env);
    await assertFails(getDocs(collection(db, 'appConfig')));
  });

  it('authenticated list/scan is denied — auth buys no enumeration either', async () => {
    const db = authedAs(env, 'reader-uid', { email: 'reader@example.com' });
    await assertFails(getDocs(collection(db, 'appConfig')));
  });

  // ── writes ───────────────────────────────────────────────────────────────

  it('client cannot write app config (Console-only)', async () => {
    const db = authedAs(env, 'attacker-uid', { email: 'attacker@example.com' });
    await assertFails(
      setDoc(doc(db, 'appConfig/mobile'), {
        minSupportedAppVersionCode: 0,
        latestAppVersionCode: 99,
      }),
    );
  });

  it('client cannot write the flag document (Console-only)', async () => {
    // A client-writable kill switch would let any signed-in user disable sync for everyone.
    const db = authedAs(env, 'attacker-uid', { email: 'attacker@example.com' });
    await assertFails(
      setDoc(doc(db, 'appConfig/flags'), {
        'sync.remote_enabled': true,
      }),
    );
  });
});
