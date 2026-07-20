const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const {
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  unauthed,
  withAdmin,
  inviteDoc,
  workspaceDoc,
  memberDoc,
} = require('./helpers');

const WID = 'ws-1';
const OWNER = 'owner-uid';
const INVITEE = 'invitee-uid';
const INVITEE_EMAIL = 'invitee@example.com';
const STRANGER = 'stranger-uid';

describe('invites — read', () => {
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
        doc(db, `workspaces/${WID}/invites/inv-1`),
        inviteDoc({ email: INVITEE_EMAIL, targetUserId: INVITEE }),
      );
    });
  });

  // The original PERMISSION_DENIED bug — recipient runs a collection-group query
  // filtered by `targetUserId == self.uid` and Firestore rejects it because the
  // analyzer can't prove the rule from query filters alone. This test catches
  // any regression in the rule structure (single OR'd `allow read` reintroduces it).
  it('recipient can collection-group query by targetUserId', async () => {
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    const q = query(
      collectionGroup(db, 'invites'),
      where('targetUserId', '==', INVITEE),
      where('status', '==', 'PENDING'),
    );
    const snap = await assertSucceeds(getDocs(q));
    if (snap.size !== 1) throw new Error(`expected 1 doc, got ${snap.size}`);
  });

  it('recipient can collection-group query by email', async () => {
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    const q = query(
      collectionGroup(db, 'invites'),
      where('email', '==', INVITEE_EMAIL),
      where('status', '==', 'PENDING'),
    );
    const snap = await assertSucceeds(getDocs(q));
    if (snap.size !== 1) throw new Error(`expected 1 doc, got ${snap.size}`);
  });

  // The targetUserId rule MUST allow uid-only auth contexts (no email claim) —
  // covers OAuth providers like Apple Hide-My-Email and anonymous-promoted users.
  it('recipient without email claim can still query by targetUserId', async () => {
    const db = authedAs(env, INVITEE);
    const q = query(
      collectionGroup(db, 'invites'),
      where('targetUserId', '==', INVITEE),
      where('status', '==', 'PENDING'),
    );
    await assertSucceeds(getDocs(q));
  });

  it('member can read the workspace invite roster', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/member-uid`),
        memberDoc({ role: 'EDITOR' }),
      );
    });
    const db = authedAs(env, 'member-uid', { email: 'member@example.com' });
    await assertSucceeds(getDoc(doc(db, `workspaces/${WID}/invites/inv-1`)));
  });

  it('stranger cannot read someone else\'s invite by direct id', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/invites/inv-1`)));
  });

  it('stranger cannot collection-group query by another user\'s targetUserId', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    const q = query(
      collectionGroup(db, 'invites'),
      where('targetUserId', '==', INVITEE),
      where('status', '==', 'PENDING'),
    );
    await assertFails(getDocs(q));
  });

  it('unauthenticated user is denied', async () => {
    const db = unauthed(env);
    await assertFails(getDoc(doc(db, `workspaces/${WID}/invites/inv-1`)));
  });
});

describe('invites — write', () => {
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
    });
  });

  it('owner can create a PENDING invite stamped with their uid', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/invites/inv-2`),
        inviteDoc({ invitedByUserId: OWNER, email: INVITEE_EMAIL, targetUserId: INVITEE }),
      ),
    );
  });

  it('non-owner cannot create an invite', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/invites/inv-3`),
        inviteDoc({ invitedByUserId: STRANGER }),
      ),
    );
  });

  it('owner cannot create an invite stamped with a different invitedByUserId', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/invites/inv-4`),
        inviteDoc({ invitedByUserId: STRANGER }),
      ),
    );
  });

  it('owner create blocked when clientVersionCode missing', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    const dto = inviteDoc({ invitedByUserId: OWNER });
    delete dto.clientVersionCode;
    await assertFails(setDoc(doc(db, `workspaces/${WID}/invites/inv-5`), dto));
  });

  it('owner create blocked when status != PENDING', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/invites/inv-6`),
        inviteDoc({ invitedByUserId: OWNER, status: 'ACCEPTED' }),
      ),
    );
  });
});

describe('invites — update / delete', () => {
  let env;

  before(async () => {
    env = await getTestEnv();
  });

  after(async () => {
    await shutdownTestEnv();
  });

  // Each test seeds a single PENDING invite under WID belonging to OWNER, addressed
  // to INVITEE (uid + email). Adjust per-test by explicit seeds where needed.
  beforeEach(async () => {
    await resetTestEnv();
    await withAdmin(env, async (db) => {
      await setDoc(doc(db, `workspaces/${WID}`), workspaceDoc({ ownerId: OWNER }));
      await setDoc(
        doc(db, `workspaces/${WID}/members/${OWNER}`),
        memberDoc({ role: 'OWNER' }),
      );
      await setDoc(
        doc(db, `workspaces/${WID}/invites/inv-1`),
        inviteDoc({ email: INVITEE_EMAIL, targetUserId: INVITEE }),
      );
    });
  });

  it('owner can cancel a PENDING invite', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'CANCELLED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  it('invitee can accept by targetUserId match', async () => {
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'ACCEPTED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  // Null-token-email regression: providers like Apple Hide-My-Email or anonymous-promoted
  // accounts may produce a token with no `email` claim. The update rule's email branch
  // does `request.auth.token.email.lower()` which errors on null. We rely on the analyzer
  // to short-circuit to the targetUserId branch — this test pins that behavior.
  it('invitee without email claim can accept via targetUserId branch', async () => {
    const db = authedAs(env, INVITEE);
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'ACCEPTED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  // Live-fire scenario: full-doc set() (the actual outbox push path), not partial
  // updateDoc. The rule must accept a payload that REWRITES every field — including
  // restoring the canonical PENDING-side data — as long as the post-state is a valid
  // accepted transition.
  it('invitee accept via full set() (mirrors outbox upload path)', async () => {
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${WID}/invites/inv-1`),
        inviteDoc({
          email: INVITEE_EMAIL,
          targetUserId: INVITEE,
          status: 'ACCEPTED',
          respondedAt: 1,
          updatedAt: 1,
        }),
      ),
    );
  });

  // Pinned regression for the live PERMISSION_DENIED on accept push (April 2026).
  // Mirrors the actual outbox payload byte-for-byte: uid+email match, full-doc set,
  // realistic timestamps, gmail-style "+alias". If this ever flips back to fails the
  // update rule has regressed.
  // Hypothesis: when the parent `workspaces/{wid}` doc is missing on Firestore but the
  // invite under it exists (owner's outbox pushed the invite before the workspace, or
  // the workspace push failed), `isOwner(wid)` calls `get(/workspaces/{wid}).data` which
  // errors on null. Firestore evaluates `||` strictly enough that a thrown error in the
  // first branch can poison the whole rule → PERMISSION_DENIED even when the targetUserId
  // branch would have matched. Lock this in: the rule must NOT crash when the parent doc
  // is missing.
  it('invitee can accept even if parent workspace doc is missing on Firestore', async () => {
    await withAdmin(env, async (db) => {
      // Seed ONLY the invite, no workspaces/{wid} parent doc, no members.
      await setDoc(
        doc(db, `workspaces/missing-parent-wid/invites/orphan-inv`),
        inviteDoc({
          email: INVITEE_EMAIL,
          targetUserId: INVITEE,
          role: 'EDITOR',
        }),
      );
    });
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/missing-parent-wid/invites/orphan-inv`), {
        status: 'ACCEPTED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  it('invitee accept regression: real uid + gmail+alias email, full set()', async () => {
    const realUid = 'wmPt0BvF1PO6FglvqPxv2Bs88rr1';
    const realEmail = 'georgeci007+14@gmail.com';
    const realWid = 'c9685ac0-cd02-4819-8f49-fd897c407d37';
    const realInviteId = '080ec199-378a-4298-a2e1-69c0f2026a16';
    const owner = 'u6Qn7KHpkSQUCMWYhovkIaDJlRC2';

    await withAdmin(env, async (db) => {
      await setDoc(doc(db, `workspaces/${realWid}`), workspaceDoc({ ownerId: owner }));
      await setDoc(
        doc(db, `workspaces/${realWid}/members/${owner}`),
        memberDoc({ role: 'OWNER' }),
      );
      await setDoc(
        doc(db, `workspaces/${realWid}/invites/${realInviteId}`),
        inviteDoc({
          email: realEmail,
          targetUserId: realUid,
          role: 'EDITOR',
          invitedByUserId: owner,
          createdAt: 1777553129019,
          updatedAt: 1777553129019,
          expiresAt: 1778762729019,
        }),
      );
    });

    const db = authedAs(env, realUid, { email: realEmail });
    await assertSucceeds(
      setDoc(
        doc(db, `workspaces/${realWid}/invites/${realInviteId}`),
        inviteDoc({
          email: realEmail,
          targetUserId: realUid,
          role: 'EDITOR',
          status: 'ACCEPTED',
          invitedByUserId: owner,
          createdAt: 1777553129019,
          updatedAt: 1777558932011,
          expiresAt: 1778762729019,
          respondedAt: 1777558932011,
        }),
      ),
    );
  });

  it('invitee can decline by email match (no targetUserId on doc)', async () => {
    // Legacy invite scenario — targetUserId never stamped, only email.
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/invites/inv-1`),
        inviteDoc({ email: INVITEE_EMAIL, targetUserId: null }),
      );
    });
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'DECLINED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  it('invitee cannot transition to a terminal state other than ACCEPTED/DECLINED', async () => {
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'CANCELLED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  // Idempotent re-push: outbox retried a mutation whose previous push already landed
  // on the server, OR another device accepted first. Client locally still has PENDING
  // (stale cache) so it retries the UPDATE. Server should accept the no-op so the
  // mutation can be marked completed and the outbox drains. Without this branch the
  // mutation loops forever as PERMISSION_DENIED.
  it('invitee can re-push the same terminal status (idempotent)', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/invites/inv-1`),
        inviteDoc({
          email: INVITEE_EMAIL,
          targetUserId: INVITEE,
          status: 'ACCEPTED',
          respondedAt: 1,
        }),
      );
    });
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'ACCEPTED',
        respondedAt: 1,
        updatedAt: 2,
      }),
    );
  });

  it('invitee cannot flip ACCEPTED → DECLINED (terminal state immutable)', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/invites/inv-1`),
        inviteDoc({
          email: INVITEE_EMAIL,
          targetUserId: INVITEE,
          status: 'ACCEPTED',
          respondedAt: 0,
        }),
      );
    });
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'DECLINED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  it('stranger cannot transition someone else\'s invite', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'ACCEPTED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  it('update blocked when clientVersionCode is missing on the resulting doc', async () => {
    // updateDoc patches without touching `clientVersionCode`, but the rule reads
    // `request.resource.data.clientVersionCode` from the FULL post-update doc.
    // Since the seeded doc carries clientVersionCode=1, this test forces the field
    // to null via updateDoc's deleteField sentinel-equivalent — admin-side overwrite.
    await withAdmin(env, async (db) => {
      const dto = inviteDoc({ email: INVITEE_EMAIL, targetUserId: INVITEE });
      delete dto.clientVersionCode;
      await setDoc(doc(db, `workspaces/${WID}/invites/inv-1`), dto);
    });
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/invites/inv-1`), {
        status: 'ACCEPTED',
        respondedAt: 1,
        updatedAt: 1,
      }),
    );
  });

  // Hard-delete is owner-only — the account-deletion purge path (issue #213).
  it('owner can hard-delete an invite (account-deletion purge)', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(deleteDoc(doc(db, `workspaces/${WID}/invites/inv-1`)));
  });

  it('non-owner cannot hard-delete an invite', async () => {
    const db = authedAs(env, INVITEE, { email: INVITEE_EMAIL });
    await assertFails(deleteDoc(doc(db, `workspaces/${WID}/invites/inv-1`)));
  });
});

