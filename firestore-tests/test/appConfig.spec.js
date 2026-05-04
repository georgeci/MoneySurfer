const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc } = require('firebase/firestore');

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
    });
  });

  it('unauthenticated user can read app config (force-update gate)', async () => {
    // Pre-auth force-update screens need to fetch this without a session.
    const db = unauthed(env);
    await assertSucceeds(getDoc(doc(db, 'appConfig/mobile')));
  });

  it('authenticated user can read app config', async () => {
    const db = authedAs(env, 'reader-uid', { email: 'reader@example.com' });
    await assertSucceeds(getDoc(doc(db, 'appConfig/mobile')));
  });

  it('client cannot write app config (Console-only)', async () => {
    const db = authedAs(env, 'attacker-uid', { email: 'attacker@example.com' });
    await assertFails(
      setDoc(doc(db, 'appConfig/mobile'), {
        minSupportedAppVersionCode: 0,
        latestAppVersionCode: 99,
      }),
    );
  });
});
