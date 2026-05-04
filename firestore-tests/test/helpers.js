const fs = require('fs');
const path = require('path');
const { initializeTestEnvironment } = require('@firebase/rules-unit-testing');

const RULES_PATH = path.resolve(__dirname, '..', '..', 'firestore.rules');

let cachedEnv = null;

async function getTestEnv() {
  if (cachedEnv) return cachedEnv;
  cachedEnv = await initializeTestEnvironment({
    projectId: 'demo-moneysurfer',
    firestore: {
      rules: fs.readFileSync(RULES_PATH, 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
  return cachedEnv;
}

async function resetTestEnv() {
  if (!cachedEnv) return;
  await cachedEnv.clearFirestore();
}

async function shutdownTestEnv() {
  if (!cachedEnv) return;
  await cachedEnv.cleanup();
  cachedEnv = null;
}

// Token shape used by Firestore rules — `email` is what the rule reads via
// `request.auth.token.email`. The `email_verified` flag isn't checked but is
// included so the token shape mirrors a real Firebase Auth ID token.
function authedAs(env, uid, opts = {}) {
  const claims = {};
  if (opts.email !== undefined) {
    claims.email = opts.email;
    claims.email_verified = opts.emailVerified !== false;
  }
  return env.authenticatedContext(uid, claims).firestore();
}

function unauthed(env) {
  return env.unauthenticatedContext().firestore();
}

// Bypasses rules — for seeding test data that the rule under test would reject.
async function withAdmin(env, fn) {
  await env.withSecurityRulesDisabled(async (context) => {
    await fn(context.firestore());
  });
}

const CLIENT_VERSION = 1;

function inviteDoc(overrides = {}) {
  return {
    workspaceId: 'ws-1',
    email: 'invitee@example.com',
    targetUserId: 'invitee-uid',
    role: 'EDITOR',
    status: 'PENDING',
    invitedByUserId: 'owner-uid',
    createdAt: 0,
    updatedAt: 0,
    expiresAt: 9999999999999,
    respondedAt: null,
    deletedAt: null,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

function workspaceDoc(overrides = {}) {
  return {
    name: 'Test workspace',
    ownerId: 'owner-uid',
    createdAt: 0,
    updatedAt: 0,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

function memberDoc(overrides = {}) {
  return {
    role: 'EDITOR',
    status: 'ACTIVE',
    email: null,
    displayName: null,
    joinedAt: 0,
    updatedAt: 0,
    clientVersionCode: CLIENT_VERSION,
    ...overrides,
  };
}

module.exports = {
  getTestEnv,
  resetTestEnv,
  shutdownTestEnv,
  authedAs,
  unauthed,
  withAdmin,
  inviteDoc,
  workspaceDoc,
  memberDoc,
  CLIENT_VERSION,
};
