const { assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { deleteDoc, doc, getDoc, setDoc, updateDoc } = require('firebase/firestore');

const {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  withAdmin,
  workspaceDoc,
  memberDoc,
  CLIENT_VERSION,
} = require('./helpers');

const WID = 'ws-1';
const OWNER = 'owner-uid';
const MEMBER = 'member-uid';
const STRANGER = 'stranger-uid';

// Account/category/transaction/budget/recurringRule rules are structurally identical:
// `read/create/update: if isMember(wid)` plus `hasValidClientVersion()` on writes.
// We exhaustively test ONE collection (`accounts`) and smoke-test the others to
// catch a missed wiring without N× redundancy.

function entityDoc(overrides = {}) {
  return {
    name: 'Test entity',
    createdAt: 0,
    updatedAt: 0,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

describe('member-gated entities (accounts)', () => {
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
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR' }),
      );
      await setDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), entityDoc());
    });
  });

  it('member can read', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  it('non-member cannot read', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  it('member can create', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(
      setDoc(doc(db, `workspaces/${WID}/accounts/acc-2`), entityDoc()),
    );
  });

  it('non-member cannot create', async () => {
    const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
    await assertFails(
      setDoc(doc(db, `workspaces/${WID}/accounts/acc-3`), entityDoc()),
    );
  });

  it('create blocked when clientVersionCode missing', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    const dto = entityDoc();
    delete dto.clientVersionCode;
    await assertFails(
      setDoc(doc(db, `workspaces/${WID}/accounts/acc-4`), dto),
    );
  });

  it('create blocked when clientVersionCode is below floor (0)', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      setDoc(
        doc(db, `workspaces/${WID}/accounts/acc-5`),
        entityDoc({ clientVersionCode: 0 }),
      ),
    );
  });

  it('member can update', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertSucceeds(
      updateDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), {
        name: 'Renamed',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  // Hard-delete is owner-only — the account-deletion purge path (issue #213).
  // Regular flows still soft-delete via a `deletedAt` tombstone update.
  it('owner can hard-delete an entity doc (account-deletion purge)', async () => {
    const db = authedAs(env, OWNER, { email: 'owner@example.com' });
    await assertSucceeds(deleteDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  it('hard-delete is denied for non-owner members', async () => {
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(deleteDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  // ── membership is ACTIVE-only: eviction/leave revokes data access ──────────
  // Member rows are soft-deleted, so isMember must check status — otherwise a
  // REMOVED/LEFT user keeps full read/write to the household's data (issue #152).

  it('an evicted (REMOVED) member can no longer read', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'REMOVED' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });

  it('an evicted (REMOVED) member can no longer write', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'REMOVED' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(
      updateDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), {
        name: 'Renamed',
        updatedAt: 1,
        clientVersionCode: 1,
      }),
    );
  });

  it('a departed (LEFT) member can no longer read', async () => {
    await withAdmin(env, async (db) => {
      await setDoc(
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR', status: 'LEFT' }),
      );
    });
    const db = authedAs(env, MEMBER, { email: 'member@example.com' });
    await assertFails(getDoc(doc(db, `workspaces/${WID}/accounts/acc-1`)));
  });
});

describe('member-gated entities (smoke for the rest)', () => {
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
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR' }),
      );
    });
  });

  for (const collection of ['categories', 'transactions', 'budgets', 'recurringRules']) {
    it(`member can create + read in ${collection}`, async () => {
      const db = authedAs(env, MEMBER, { email: 'member@example.com' });
      const path = `workspaces/${WID}/${collection}/entry-1`;
      await assertSucceeds(setDoc(doc(db, path), entityDoc()));
      await assertSucceeds(getDoc(doc(db, path)));
    });

    it(`non-member is blocked from ${collection}`, async () => {
      const db = authedAs(env, STRANGER, { email: 'stranger@example.com' });
      const path = `workspaces/${WID}/${collection}/entry-2`;
      await assertFails(setDoc(doc(db, path), entityDoc()));
    });
  }
});

// ── write-shape validation: poison-document guard (issue #156) ────────────────
// A wrong-typed field on an entity write used to sail through the rules (only
// membership + clientVersionCode were checked) and then crash every co-member's
// pull when decode() hit the bad type. The rules now reject the malformed write.
// Contract: a field is type-checked only WHEN PRESENT — a minimal doc that omits
// default-valued fields must still pass — so we assert both the reject and the
// accept side.
describe('entity write-shape validation (issue #156)', () => {
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
        doc(db, `workspaces/${WID}/members/${MEMBER}`),
        memberDoc({ role: 'EDITOR' }),
      );
      await setDoc(doc(db, `workspaces/${WID}/accounts/acc-1`), entityDoc());
    });
  });

  const asMember = () => authedAs(env, MEMBER, { email: 'member@example.com' });

  // AccountDoc — mirrors data-remote/.../RemoteDtos.kt
  function accountDoc(overrides = {}) {
    return {
      name: 'Checking',
      type: 'CASH',
      currency: 'USD',
      balance: 1000,
      archived: false,
      updatedAt: 1,
      deletedAt: null,
      clientVersionCode: CLIENT_VERSION,
      ...overrides,
    };
  }

  // TransactionDoc — mirrors data-remote/.../RemoteDtos.kt
  function transactionDoc(overrides = {}) {
    return {
      accountId: 'acc-1',
      amount: 500,
      currencyCode: 'USD',
      note: 'Groceries',
      operationAt: 1,
      operationDate: '2026-07-15',
      type: 'EXPENSE',
      status: 'ACTUAL',
      createdAt: 1,
      updatedAt: 1,
      clientVersionCode: CLIENT_VERSION,
      ...overrides,
    };
  }

  it('accepts a fully-populated, correctly-typed account', async () => {
    await assertSucceeds(
      setDoc(doc(asMember(), `workspaces/${WID}/accounts/acc-ok`), accountDoc()),
    );
  });

  it('accepts a minimal account (default-valued fields omitted)', async () => {
    await assertSucceeds(
      setDoc(
        doc(asMember(), `workspaces/${WID}/accounts/acc-min`),
        { name: 'Sparse', updatedAt: 1, clientVersionCode: CLIENT_VERSION },
      ),
    );
  });

  it('rejects an account whose numeric balance is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/accounts/acc-bad`),
        accountDoc({ balance: 'lots' }),
      ),
    );
  });

  it('rejects an account whose boolean archived flag is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/accounts/acc-bad2`),
        accountDoc({ archived: 'yes' }),
      ),
    );
  });

  it('rejects an account whose string name is a number', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/accounts/acc-bad3`),
        accountDoc({ name: 42 }),
      ),
    );
  });

  it('rejects a poison update of an existing account (balance as string)', async () => {
    await assertFails(
      updateDoc(doc(asMember(), `workspaces/${WID}/accounts/acc-1`), {
        balance: 'abc',
        updatedAt: 2,
        clientVersionCode: CLIENT_VERSION,
      }),
    );
  });

  it('accepts a nullable field set explicitly to null (deletedAt)', async () => {
    await assertSucceeds(
      setDoc(
        doc(asMember(), `workspaces/${WID}/accounts/acc-null`),
        accountDoc({ deletedAt: null }),
      ),
    );
  });

  it('rejects a nullable field with a wrong non-null type (deletedAt as string)', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/accounts/acc-baddel`),
        accountDoc({ deletedAt: 'soon' }),
      ),
    );
  });

  // BudgetDoc — mirrors data-remote/.../RemoteDtos.kt
  function budgetDoc(overrides = {}) {
    return {
      name: 'Groceries',
      categoryIds: ['cat-1', 'cat-2'],
      amount: 50000,
      period: 'MONTHLY',
      startDate: '2026-07-01',
      alertPercent: 80,
      isActive: true,
      rollover: false,
      createdAt: 1,
      updatedAt: 1,
      deletedAt: null,
      clientVersionCode: CLIENT_VERSION,
      ...overrides,
    };
  }

  it('accepts a fully-populated, correctly-typed budget', async () => {
    await assertSucceeds(
      setDoc(doc(asMember(), `workspaces/${WID}/budgets/b-ok`), budgetDoc()),
    );
  });

  // Empty categoryIds means "all expense categories" — a legitimate budget, not a malformed one.
  it('accepts a budget with an empty categoryIds list', async () => {
    await assertSucceeds(
      setDoc(
        doc(asMember(), `workspaces/${WID}/budgets/b-all`),
        budgetDoc({ categoryIds: [] }),
      ),
    );
  });

  it('rejects a budget whose categoryIds is a CSV string instead of a list', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/budgets/b-csv`),
        budgetDoc({ categoryIds: 'cat-1,cat-2' }),
      ),
    );
  });

  it('rejects a budget whose amount is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/budgets/b-amount`),
        budgetDoc({ amount: 'lots' }),
      ),
    );
  });

  it('rejects a budget whose rollover flag is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/budgets/b-rollover`),
        budgetDoc({ rollover: 'yes' }),
      ),
    );
  });

  it('rejects a budget whose startDate is a number', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/budgets/b-start`),
        budgetDoc({ startDate: 20260701 }),
      ),
    );
  });

  it('accepts a budget tombstone update (deletedAt set)', async () => {
    await setDoc(doc(asMember(), `workspaces/${WID}/budgets/b-tomb`), budgetDoc());
    await assertSucceeds(
      updateDoc(doc(asMember(), `workspaces/${WID}/budgets/b-tomb`), {
        deletedAt: 2,
        updatedAt: 2,
        clientVersionCode: CLIENT_VERSION,
      }),
    );
  });

  it('rejects a poison update of an existing budget (alertPercent as string)', async () => {
    await setDoc(doc(asMember(), `workspaces/${WID}/budgets/b-poison`), budgetDoc());
    await assertFails(
      updateDoc(doc(asMember(), `workspaces/${WID}/budgets/b-poison`), {
        alertPercent: 'eighty',
        updatedAt: 2,
        clientVersionCode: CLIENT_VERSION,
      }),
    );
  });

  it('accepts a correctly-typed transaction', async () => {
    await assertSucceeds(
      setDoc(doc(asMember(), `workspaces/${WID}/transactions/tx-ok`), transactionDoc()),
    );
  });

  // CategoryDoc — mirrors data-remote/.../RemoteDtos.kt
  function categoryDoc(overrides = {}) {
    return {
      name: 'Groceries',
      type: 'EXPENSE',
      parentId: null,
      createdAt: 1,
      updatedAt: 1,
      deletedAt: null,
      systemKind: null,
      iconKey: 'cash',
      hue: 162,
      clientVersionCode: CLIENT_VERSION,
      ...overrides,
    };
  }

  it('accepts a category carrying the new iconKey + hue fields', async () => {
    await assertSucceeds(
      setDoc(doc(asMember(), `workspaces/${WID}/categories/cat-ok`), categoryDoc()),
    );
  });

  // The compatibility case the deploy ordering hangs on: a client that predates iconKey/hue
  // omits both, and its writes must keep validating after the rules ship.
  it('accepts a category from an older client that omits iconKey and hue', async () => {
    const { iconKey, hue, ...legacy } = categoryDoc();
    await assertSucceeds(
      setDoc(doc(asMember(), `workspaces/${WID}/categories/cat-legacy`), legacy),
    );
  });

  it('rejects a category whose hue is a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/categories/cat-hue`),
        categoryDoc({ hue: 'teal' }),
      ),
    );
  });

  it('rejects a category whose iconKey is a number', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/categories/cat-icon`),
        categoryDoc({ iconKey: 3 }),
      ),
    );
  });

  it('accepts a category nested under a parent', async () => {
    await assertSucceeds(
      setDoc(
        doc(asMember(), `workspaces/${WID}/categories/cat-child`),
        categoryDoc({ parentId: 'cat-ok' }),
      ),
    );
  });

  it('rejects a poison update of an existing category (hue as string)', async () => {
    await setDoc(doc(asMember(), `workspaces/${WID}/categories/cat-poison`), categoryDoc());
    await assertFails(
      updateDoc(doc(asMember(), `workspaces/${WID}/categories/cat-poison`), {
        hue: 'teal',
        updatedAt: 2,
        clientVersionCode: CLIENT_VERSION,
      }),
    );
  });

  it('rejects the issue scenario: transaction amount as a string', async () => {
    await assertFails(
      setDoc(
        doc(asMember(), `workspaces/${WID}/transactions/tx-poison`),
        transactionDoc({ amount: 'abc' }),
      ),
    );
  });
});
