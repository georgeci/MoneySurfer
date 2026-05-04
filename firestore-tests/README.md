# Firestore rules tests

Mocha tests for `firestore.rules` running against the Firebase emulator. Catches regressions like the byUid `PERMISSION_DENIED` bug where a single OR'd `allow read` defeated the collection-group query analyzer.

## Setup

Requires Node.js, Java (for the emulator), and the Firebase CLI.

```sh
cd firestore-tests
npm install
```

## Run

```sh
npm test
```

This boots the Firestore emulator, runs the spec files, and tears down. No real Firebase project is touched — it uses a fake `demo-moneysurfer` project id that the emulator special-cases (no auth required).

## Watch mode

If you'd rather keep the emulator running and re-run tests on save:

```sh
# terminal 1
firebase emulators:start --only firestore --project demo-moneysurfer

# terminal 2
cd firestore-tests
npm run test:watch
```

## Adding tests

- One `describe` block per logical area (`invites — read`, `invites — write`, etc.).
- Use `assertSucceeds` / `assertFails` from `@firebase/rules-unit-testing` — they reject promises with helpful diagnostics.
- Seed data with `withAdmin(env, async db => { … })` to bypass rules.
- Use `authedAs(env, uid, { email })` for the contexts under test.

Helpers live in [test/helpers.js](test/helpers.js).
