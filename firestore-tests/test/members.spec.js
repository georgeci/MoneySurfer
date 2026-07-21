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
  inviteDoc,
} = require('./helpers');

const WID = 'ws-1';
const OWNER = 'owner-uid';
const MEMBER = 'member-uid';
const NEWCOMER = 'newcomer-uid';
const NEWCOMER_EMAIL = 'newcomer@example.com';
const STRANGER = 'stranger-uid';
const INVITE_ID = 'inv-newcomer';

// Seed a single invite under WID (bypassing rules) so the self-create tests can
// exercise the `hasJoinableInvite` gate. Defaults target NEWCOMER by uid + email.
async function seedInvite(env, overrides = {}) {
  await withAdmin(env, async (db) => {
    await setDoc(
      doc(db, `workspaces/${WID}/invites/${INVITE_ID}`),
      inviteDoc({
        email: NEWCOMER_EMAIL,
        targetUserId: NEWCOMER,
        invitedByUserId: OWNER,
        ...overrides,
      }),
    );
  });
}

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

  it('an evicted (REMOVED) member can no longer read the roster', async () => {
    // isMember is ACTIVE-only, so a soft-removed row no longer counts as membership.
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'REMOVED' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/members/${OWNER}`)));
  });

  // ── self-create (accept invite) ─────────────────────────────────────────
  //
  // Tenant-isolation gate (issue #152): a self-created member row is accepted ONLY
  // when it points (via `inviteId`) at an owner-issued invite that admits the caller.
  // Invite-less self-join is the vulnerability these tests pin closed.

  it('non-invited stranger CANNOT self-create a member row', async () => {
    // The core exploit: any signed-in user who learns a workspace id used to be able
    // to write their own member row and read/write the whole household's data. With no
    // invite seeded — and no inviteId on the doc — the create must be denied.
    const db = authedAs(env, NEWCOMER, { email: NEWCOMER_EMAIL });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', status: 'ACTIVE' }),
      ),
    );
  });

  it('stranger cannot self-create even by naming a non-existent inviteId', async () => {
    const db = authedAs(env, NEWCOMER, { email: NEWCOMER_EMAIL });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', inviteId: 'does-not-exist' }),
      ),
    );
  });

  it('invited user CAN self-create when the doc points at their PENDING invite', async () => {
    await seedInvite(env); // PENDING invite targeting NEWCOMER by uid + email
    const db = authedAs(env, NEWCOMER, { email: NEWCOMER_EMAIL });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', status: 'ACTIVE', inviteId: INVITE_ID }),
      ),
    );
  });

  it('invited user CAN self-create referencing an already-ACCEPTED invite (push-order/idempotent)', async () => {
    // The invitee's PENDING→ACCEPTED flip may land before the member create is pushed,
    // or the member create may be retried by the outbox. An ACCEPTED invite still admits.
    await seedInvite(env, { status: 'ACCEPTED' });
    const db = authedAs(env, NEWCOMER, { email: NEWCOMER_EMAIL });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', status: 'ACTIVE', inviteId: INVITE_ID }),
      ),
    );
  });

  it('invited user CAN self-create via an email-only invite (legacy, no targetUserId)', async () => {
    await seedInvite(env, { targetUserId: null });
    const db = authedAs(env, NEWCOMER, { email: NEWCOMER_EMAIL });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', status: 'ACTIVE', inviteId: INVITE_ID }),
      ),
    );
  });

  it('attacker CANNOT self-create by pointing at an invite addressed to someone else', async () => {
    // A real PENDING invite exists in the workspace, but it targets a different user.
    // The attacker names its id on their own member doc — the rule must still deny,
    // because the invite neither targets their uid nor matches their email.
    await seedInvite(env, { targetUserId: 'victim-uid', email: 'victim@example.com' });
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/members/${STRANGER}`),
        memberDoc({ role: 'EDITOR', inviteId: INVITE_ID }),
      ),
    );
  });

  it('invited user CANNOT self-create against a CANCELLED invite', async () => {
    // Owner revoked the invite before it was accepted — it must no longer grant a join.
    await seedInvite(env, { status: 'CANCELLED' });
    const db = authedAs(env, NEWCOMER, { email: NEWCOMER_EMAIL });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', inviteId: INVITE_ID }),
      ),
    );
  });

  it('user cannot create another user\'s member row (only self)', async () => {
    await seedInvite(env);
    const db = authedAs(env, NEWCOMER, { email: NEWCOMER_EMAIL });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/members/some-other-uid`),
        memberDoc({ role: 'EDITOR', inviteId: INVITE_ID }),
      ),
    );
  });

  it('owner can create a member row for someone else (no invite reference needed)', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/members/${NEWCOMER}`),
        memberDoc({ role: 'EDITOR', status: 'ACTIVE' }),
      ),
    );
  });

  it('owner can self-create their own OWNER row on workspace creation (no invite)', async () => {
    // Fresh workspace with no member roster yet: the owner adds themselves. The self
    // branch (uid == auth.uid) finds no invite, but evaluation falls through to the
    // owner branch, which admits them without any invite reference.
    const WID2 = 'ws-2';
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, `workspaces/${WID2}`), workspaceDoc({ ownerId: OWNER }));
    });
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID2}/members/${OWNER}`),
        memberDoc({ role: 'OWNER', status: 'ACTIVE' }),
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

  it('an evicted (REMOVED) member CANNOT self-resurrect to ACTIVE', async () => {
    // Owner eviction defeat (issue #152): a REMOVED user must not be able to flip their
    // own row back to ACTIVE. Rejoining has to go through a fresh owner invite.
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'REMOVED' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`), {
        status: 'ACTIVE',
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

  // Hard-delete is owner-only — the account-deletion purge path (issue #213).
  // Eviction and leaving still soft-delete via status=REMOVED / LEFT.
  it('owner can hard-delete a member row (account-deletion purge)', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(deleteDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`)));
  });

  it('member cannot hard-delete a member row (not even their own)', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(deleteDoc(doc(db, `workspaces/${WID}/members/${MEMBER}`)));
  });

  it('unauthenticated user is denied', async () => {
    const db = unauthed(env);
    await assertFails(getDoc(doc(db, `workspaces/${WID}/members/${OWNER}`)));
  });
});
