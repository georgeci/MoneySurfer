const assert = require('assert');
const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { collection, doc, getDoc, getDocs, query, setDoc, where } = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  withAdmin,
  workspaceDoc,
  memberDoc,
} = require('./helpers');

// Issue #342. The shipped online build wrote `users/{uid}.workspaceIds` entries whose
// `workspaces/{wid}` document was never created: `SyncFeatureFlag` gated the syncer that
// pushes the workspace, but not the `addWorkspaceRef` call that records it.
//
// The client fix (skip an unreadable workspace instead of failing the whole pull) was written
// against an *unverified* assumption about what such a read does. These tests pin the actual
// behaviour down at the rules layer, which is what decides it.
//
// Note on scope: this exercises the JS SDK. Whether GitLive surfaces the denial as a thrown
// `FirebaseFirestoreException` or swallows it is a wrapper concern one layer up — but the
// client tolerates both shapes precisely because that layer is not pinned here.

const PHANTOM = 'ws-phantom';
const REAL = 'ws-real';
const UID = 'alice-uid';

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

describe('dangling users/{uid}.workspaceIds refs', () => {
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
      // The damaged state: the user document claims two workspaces, but only one of them
      // exists. PHANTOM has neither a root document nor a members subcollection — exactly
      // what `addWorkspaceRef` left behind when the push was a no-op.
      await setDoc(
        doc(db, `users/${UID}`),
        userDoc({ workspaceIds: [PHANTOM, REAL], defaultWorkspaceId: PHANTOM }),
      );
      await setDoc(doc(db, `workspaces/${REAL}`), workspaceDoc({ ownerId: UID }));
      await setDoc(doc(db, `workspaces/${REAL}/members/${UID}`), memberDoc({ role: 'OWNER' }));
    });
  });

  it('the user can read their own document and does see the phantom ref', async () => {
    // This is why the bug is invisible on the client: the ref reads back fine. Nothing in
    // `users/{uid}` says the workspace it points at does not exist.
    const db = authedAs(env, UID, { email: 'alice@example.com' });
    const snapshot = await assertSucceeds(getDoc(doc(db, `users/${UID}`)));

    assert.deepStrictEqual(snapshot.data().workspaceIds, [PHANTOM, REAL]);
    assert.strictEqual(snapshot.data().defaultWorkspaceId, PHANTOM);
  });

  it('getting the phantom workspace is DENIED, not an empty snapshot', async () => {
    // The question the audit left open. `allow get: if isMember(wid)` calls
    // `exists(.../members/{uid})`, which is false when nothing was ever created — so the
    // read is rejected outright rather than returning `exists === false`. A client that
    // treated a missing workspace as "nothing to pull" would instead take an error path.
    const db = authedAs(env, UID, { email: 'alice@example.com' });

    const error = await assertFails(getDoc(doc(db, `workspaces/${PHANTOM}`)));

    assert.strictEqual(error.code, 'permission-denied');
  });

  it('a missing workspace with a surviving member row reads back empty instead', async () => {
    // The discriminator: the denial above comes from *membership*, not from absence. Where
    // the member row survives but the root document does not, the same read succeeds and
    // reports `exists === false` — so "workspace not found" and "workspace not yours" are
    // genuinely different outcomes, and only the second one should be skipped as stale.
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, `workspaces/${PHANTOM}/members/${UID}`), memberDoc({ role: 'OWNER' }));
    });
    const db = authedAs(env, UID, { email: 'alice@example.com' });

    const snapshot = await assertSucceeds(getDoc(doc(db, `workspaces/${PHANTOM}`)));

    assert.strictEqual(snapshot.exists(), false);
  });

  it('subcollections of the phantom workspace are denied too', async () => {
    // The other SDK shape the audit flagged: if the root `get()` were to come back empty,
    // the failure would surface on the first subcollection query instead. It is denied on
    // the same `isMember` check, so a client cannot get past a phantom ref either way.
    const db = authedAs(env, UID, { email: 'alice@example.com' });

    const error = await assertFails(
      getDocs(collection(db, `workspaces/${PHANTOM}/accounts`)),
    );

    assert.strictEqual(error.code, 'permission-denied');
  });

  it('invites are the exception: a targetUserId-filtered query succeeds and comes back empty', async () => {
    // `invites` is the one subcollection not gated on membership — `allow read: if
    // resource.data.targetUserId == request.auth.uid` lets the analyzer admit a query that
    // carries the matching filter, member row or not. So the phase-2 invite pull does NOT
    // trip over a phantom ref; it just finds nothing.
    //
    // Worth stating explicitly because it cuts the other way from every assertion above: the
    // client cannot infer "this workspace is unreadable" from the invites collection.
    const db = authedAs(env, UID, { email: 'alice@example.com' });

    const snapshot = await assertSucceeds(
      getDocs(
        query(
          collection(db, `workspaces/${PHANTOM}/invites`),
          where('targetUserId', '==', UID),
        ),
      ),
    );

    assert.strictEqual(snapshot.empty, true);
  });

  it('an unfiltered invites query on a phantom workspace is denied', async () => {
    // The contrast that proves the clause above is doing the work: drop the filter and the
    // read falls back to `isMember`, which fails. This is why the pull uses
    // `fetchInvitesForUser` rather than listing the collection.
    const db = authedAs(env, UID, { email: 'alice@example.com' });

    const error = await assertFails(getDocs(collection(db, `workspaces/${PHANTOM}/invites`)));

    assert.strictEqual(error.code, 'permission-denied');
  });

  it('the healthy workspace alongside it stays readable', async () => {
    // What makes skipping the right remedy: one bad ref says nothing about the others, so
    // aborting the whole pull would have thrown away data the user can actually reach.
    const db = authedAs(env, UID, { email: 'alice@example.com' });

    const snapshot = await assertSucceeds(getDoc(doc(db, `workspaces/${REAL}`)));

    assert.strictEqual(snapshot.exists(), true);
    assert.strictEqual(snapshot.data().ownerId, UID);
  });
});
