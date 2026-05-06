# CPD — copy-paste detection (local)

CPD (PMD's Copy-Paste Detector) flags duplicated Kotlin blocks the same way
SonarCloud does. It runs locally so duplication is caught **before** push,
not after Sonar comments on the PR.

## When to run

Run before every commit that adds or modifies Kotlin code:

```bash
./gradlew cpdCheck
```

It is fast (no compilation, just tokenization) and incremental.

If the task reports duplicates, read the report and decide per-case:

- Genuine duplication of logic → extract a function/class.
- Repetitive but distinct (e.g. test fixtures, exhaustive `when` branches,
  generated-looking boilerplate) → leave it; do not contort the code to
  silence CPD.

Do not raise `minimumTokenCount` to hide a real duplicate — fix the code
or accept the warning with a brief justification in the PR.

## Reports

After a run, look here:

- `build/reports/cpd/cpdCheck.text` — human-readable, easiest to skim.
- `build/reports/cpd/cpdCheck.xml` — same data, machine-readable.

Each entry lists the duplicated token count, line ranges, and source files.

## Configuration

Defined in the root [`build.gradle.kts`](../../build.gradle.kts) (search for the `cpd { ... }` block):

- `language = "kotlin"`, `minimumTokenCount = 100` — Sonar's default
  threshold for Kotlin. Smaller blocks are noise.
- `ignoreFailures = true` — surfaces duplicates without breaking the build.
  Flip to `false` once the existing duplication backlog is cleared.
- Excludes: `build/`, `generated/`, any `*Test*` source set, `iosApp/`,
  `iosAppOffline/`. Test code repeats by nature; production code is what
  matters.

PMD version is pinned to 7.7.0 inline in the task config.

## Relation to Sonar

SonarCloud already runs CPD in CI on every PR. The local task exists so the
AI agent can self-check before committing — same engine, same threshold,
just earlier in the loop. There is no GitHub Actions wiring for CPD; CI
duplication signal comes from Sonar.
