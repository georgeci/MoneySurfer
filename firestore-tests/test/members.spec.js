const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { deleteDoc, doc, getDoc, setDoc, updateDoc } = require('firebase/firestore');

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
const NEWCOMER = 'newcomer-uid';
const STRANGER = 'stranger-uid';

describe('workspaces/{wid}/members/{uid}', () => {
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

  // ── reads ────────────────────────────────────────────────────────────────

  it('member can read another member\'s row (roster visibility)', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(getDoc(doc(db, `workspaces/${WID}/members/${OWNER}`)));
  });

  it('non-member cannot read the roster', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/members/${OWNER}`)));
  });

  // ── self-create (accept invite) ─────────────────────────────────────────

  it('signed-in user can self-create their own member row (accept invite path)', async () => {
    const db = authedAs(env, NEWCOMER, { email: 'newcomer@example.com' });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', status: 'ACTIVE' }),
      ),
    );
  });

  it('user cannot create another user\'s member row (only self)', async () => {
    const db = authedAs(env, NEWCOMER, { email: 'newcomer@example.com' });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/members/some-other-uid`),
        memberDoc({ role: 'EDITOR' }),
      ),
    );
  });

  it('owner can create a member row for someone else', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', status: 'ACTIVE' }),
      ),
    );
  });

  // ── self-update (leave / accept-flow) ───────────────────────────────────

  it('user can self-update status to LEFT', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), {
        status: 'LEFT',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('user CANNOT self-promote their role', async () => {
    // Critical privilege-escalation guard: a non-owner editor flipping themselves
    // to OWNER would grant unrestricted workspace control.
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), {
        role: 'OWNER',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('user CANNOT self-set status to REMOVED', async () => {
    // REMOVED is owner-only — soft-leave uses LEFT, not REMOVED, so a member
    // can't fake an admin action.
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), {
        status: 'REMOVED',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('owner can promote a member\'s role', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), {
        role: 'OWNER',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('owner can mark a member as REMOVED', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), {
        status: 'REMOVED',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('non-owner cannot edit another member\'s row', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/members/${OWNER}`), {
        role: 'EDITOR',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  // ── delete ─────────────────────────────────────────────────────────────

  it('hard-delete is denied (soft-delete via status=REMOVED)', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertFails(deleteDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`)));
  });

  it('unauthenticated user is denied', async () => {
    const db = unauthed(env);
    await assertFails(getDoc(doc(db, `workspaces/${WID}/members/${OWNER}`)));
  });
});
