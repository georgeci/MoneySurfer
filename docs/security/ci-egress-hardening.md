# CI egress hardening — harden-runner + secret scanning

<!-- DOCS:TOC -->
## Contents
- [CI egress hardening — harden-runner + secret scanning](#ci-egress-hardening-harden-runner-secret-scanning)
- [1. step-security/harden-runner (egress audit)](#1-step-securityharden-runner-egress-audit)
  - [Rollout: audit → block](#rollout-audit-block)
- [2. Secret scanning + push protection (manual — repo settings)](#2-secret-scanning-push-protection-manual-repo-settings)
<!-- DOCS:END -->

Tracks [#180](https://github.com/georgeci/MoneySurfer/issues/180). Follow-up to
the [2026-07-15 security audit](audit-2026-07-15.md) (advice round). The base
posture is already good — third-party actions are SHA-pinned, jobs carry
minimal `permissions`, and fork PRs are cut off from secrets
([supply-chain.md](supply-chain.md)). This is the next layer: watch what the
secret-holding CI jobs talk to over the network, and stop a key from ever being
committed by accident.

<!-- AI:SECTION id=ci-egress-hardening task=ci,security,supply-chain -->
## 1. step-security/harden-runner (egress audit)

[`harden-runner`](https://github.com/step-security/harden-runner) installs a
network monitor as the **first step** of a job and records every outbound
connection the job's steps make. It is the control for the
`tj-actions/changed-files` class of incident: a compromised (but still
SHA-pinned) third-party action that exfiltrates secrets is caught the moment it
phones home to an unexpected host, while the job holds decoded Firebase config,
`SONAR_TOKEN`, or the E2E credentials.

It is added to every job that decodes a repository secret — and **only** those
jobs; the doc-only gate jobs and the offline build lanes hold nothing worth
auditing:

| Workflow | Job | Secrets in scope |
|---|---|---|
| `ci.yml` | `junit` | Firebase config |
| `ci.yml` | `sonar` | Firebase config, `SONAR_TOKEN`, `GITHUB_TOKEN` |
| `codeql.yml` | `analyze` | Firebase config (java-kotlin tracer build) |
| `nightly.yml` | `android-compile` | Firebase config |
| `nightly.yml` | `ios-build` | Firebase config (iOS plist) |
| `nightly.yml` | `maestro-android` | Firebase config, E2E creds |
| `nightly.yml` | `maestro-ios` | Firebase config (iOS plist), E2E creds |

Pinned to a commit SHA with a version comment, same convention as every other
third-party action in the repo:

```yaml
- name: Harden the runner (egress audit)
  uses: step-security/harden-runner@bf7454d06d71f1098171f2acdf0cd4708d7b5920 # v2.20.0
  with:
    egress-policy: audit
```

Notes:

- **`audit`, not `block`, to start.** Audit only *logs* egress (plus a global
  block-list of known-malicious IOCs that is always enforced, even in audit
  mode); it does not fail a job for contacting a new host. This lets a baseline
  of legitimate endpoints accumulate before any enforcement is switched on.
- **macOS runners** (`ios-build`, `maestro-ios`) support audit mode
  out-of-the-box in harden-runner ≥ v2 — it logs egress there too and never
  blocks. No runner-specific configuration is needed.
- **CodeQL** runs `harden-runner` unconditionally (no `if:`) so both matrix
  legs are baselined, even though only the `java-kotlin` leg decodes Firebase
  config.

### Rollout: audit → block

1. Land the `audit` steps (this change) and let PR + nightly runs accumulate a
   baseline. Each run's step summary links a StepSecurity "insights" page
   listing the destinations that job contacted.
2. After a few weeks of green runs, read the observed allow-list for each job
   from those insights (Gradle/Maven mirrors, `github.com`,
   `*.githubusercontent.com`, Firebase, SonarCloud, Allure/Codecov download
   hosts, npm for the Firestore job, …).
3. Flip the highest-value Ubuntu jobs to `egress-policy: block` with an explicit
   `allowed-endpoints:` list; keep macOS on `audit` (block mode there needs
   extra setup and the iOS jobs are the least likely exfil path). Roll out one
   job at a time so a missing endpoint fails a single job, not the whole matrix.

## 2. Secret scanning + push protection (manual — repo settings)

GitHub secret scanning and **push protection** cannot be enabled from the repo
tree; they are toggles under **Settings → Code security and analysis**. They are
cheap insurance given how often this repo handles Firebase config from secrets:
push protection rejects a `git push` that contains a recognised credential
before it ever reaches the remote.

**Maintainer action required** (one-time, on `github.com/georgeci/MoneySurfer`):

1. Settings → Code security and analysis.
2. Enable **Secret scanning**.
3. Enable **Push protection** under it.

Both are free for this repository's visibility tier. Nothing in the codebase
changes; this section is the tracked record that the toggle is the remaining
open item for #180.
<!-- AI:END -->
