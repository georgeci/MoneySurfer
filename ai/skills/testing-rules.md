# Skill - Testing Rules

<!-- DOCS:TOC -->
## Contents
- [Skill - Testing Rules](#skill---testing-rules)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Pick the narrowest validation that covers the changed module.
- Use Firestore rules tests for rules changes.
- Device integration tests need emulator/device setup.
- Read this before choosing tests.

READ WHEN:
- adding tests
- running validation
- touching sync
- touching persistence

<!-- AI:SECTION id=testing-rules task=testing,qa,validation -->
## Rules

- Do not run broad builds by default.
- Compile the edited module when logic changes are narrow.
- Add unit tests for domain and ViewModel behavior.
- Add integration tests for cross-module persistence/sync behavior.
<!-- AI:END -->
