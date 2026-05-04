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
  workspaceDoc,
  memberDoc,
} = require('./helpers');

const WID = 'ws-1';
const OWNER = 'owner-uid';
const MEMBER = 'member-uid';
const STRANGER = 'stranger-uid';

describe('workspaces/{wid}', () => {
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
        doc(db, `workspaces/${WID}/members/${OWNER}`),
        memberDoc({ role: 'OWNER' }),
      );
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR' }),
      );
    });
  });

  it('member can get the workspace doc', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(getDoc(doc(db, `workspaces/${WID}`)));
  });

  it('non-member cannot get the workspace doc', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}`)));
  });

  it('list across all workspaces is denied — workspace get/list both gated by isMember', async () => {
    // The `list` rule requires isMember(wid), but for a top-level
    // collection scan the analyzer can't bind wid → query rejected.
    // This documents intentional behavior: clients fetch workspaces by id
    // (hydrated from `users/{uid}.workspaceIds`), never by listing the root.
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(getDocs(collection(db, 'workspaces')));
  });

  it('signed-in user can create a workspace they own', async () => {
    const db = authedAs(env, 'creator-uid', { email: 'creator@example.com' });
    await assertSucceeds(
      setDoc(
        doc(db, 'workspaces/ws-new'),
        workspaceDoc({ ownerId: 'creator-uid' }),
      ),
    );
  });

  it('user cannot create a workspace owned by someone else', async () => {
    const db = authedAs(env, 'creator-uid', { email: 'creator@example.com' });
    await assertFails(
      setDoc(
        doc(db, 'workspaces/ws-new-2'),
        workspaceDoc({ ownerId: 'someone-else' }),
      ),
    );
  });

  it('create blocked when clientVersionCode missing', async () => {
    const db = authedAs(env, 'creator-uid', { email: 'creator@example.com' });
    const dto = workspaceDoc({ ownerId: 'creator-uid' });
    delete dto.clientVersionCode;
    await assertFails(setDoc(doc(db, 'workspaces/ws-new-3'), dto));
  });

  it('create blocked when clientVersionCode is below floor', async () => {
    const db = authedAs(env, 'creator-uid', { email: 'creator@example.com' });
    await assertFails(
      setDoc(
        doc(db, 'workspaces/ws-new-4'),
        workspaceDoc({ ownerId: 'creator-uid', clientVersionCode: 0 }),
      ),
    );
  });

  it('owner can update workspace metadata', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}`), {
        name: 'Renamed',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('non-owner member cannot update workspace metadata', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}`), {
        name: 'Renamed by member',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('hard-delete is denied (soft-delete via deletedAt update only)', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertFails(deleteDoc(doc(db, `workspaces/${WID}`)));
  });

  it('unauthenticated user is denied', async () => {
    const db = unauthed(env);
    await assertFails(getDoc(doc(db, `workspaces/${WID}`)));
  });
});
