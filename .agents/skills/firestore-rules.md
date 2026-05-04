# Skill - Firestore Rules

Use for `firestore.rules`, `firestore.indexes.json`, remote schema, and
permission bugs.

## Required Reading

- [../../docs/architecture/persistence.md](../../docs/architecture/persistence.md)
- [../../docs/architecture/firestore-rules-bugs.md](../../docs/architecture/firestore-rules-bugs.md)
- [../../firestore-tests/README.md](../../firestore-tests/README.md)

## Rules

- Rules must match workspace membership model.
- Prefer soft-delete/tombstone behavior where sync expects it.
- Add or update emulator tests for permission changes.
- Use fake `demo-moneysurfer` project in tests.

## Validation

```bash
cd firestore-tests
npm test
```
