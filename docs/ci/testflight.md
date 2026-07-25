# TestFlight distribution (iOS online)

<!-- DOCS:TOC -->
## Contents
- [TestFlight distribution (iOS online)](#testflight-distribution-ios-online)
- [TL;DR for agents](#tldr-for-agents)
- [Scope and triggers](#scope-and-triggers)
- [Required secrets](#required-secrets)
- [App Store Connect API key](#app-store-connect-api-key)
- [Build number and artifacts](#build-number-and-artifacts)
- [Relation to local releases](#relation-to-local-releases)
<!-- DOCS:END -->

## TL;DR for agents

- `.github/workflows/ios-distribute.yml` archives the online `iosApp`, uploads
  it to TestFlight, and retains the exported IPA for 14 days.
- It runs manually or daily at 04:17 UTC; scheduled runs skip an unchanged
  `main`.
- CI delegates archive, export, and upload to `scripts/ios/release.sh main`.

READ WHEN:
- configuring iOS tester distribution
- rotating the App Store Connect API key
- debugging the online iOS release workflow
- changing iOS build numbering or release artifacts

<!-- AI:SECTION id=ios-testflight-distribution task=ios,testflight,ci,release -->
## Scope and triggers

[`ios-distribute.yml`](../../.github/workflows/ios-distribute.yml) distributes
the online [`iosApp`](../../iosApp/) only. The offline target is not uploaded by
this workflow.

| Trigger | Behavior |
| --- | --- |
| `workflow_dispatch` | Runs from the Actions "Run workflow" button. |
| `schedule` | Runs daily at 04:17 UTC. |

The scheduled run compares the current `main` commit with the last successful
run and exits without building when the SHA is unchanged. Manual runs always
build. The button and schedule are available only after the workflow exists on
the default branch. Missing required secrets fail a manual run; scheduled runs
emit a notice and skip until initial setup is complete.

## Required secrets

| Secret | Contents |
| --- | --- |
| `GOOGLE_SERVICE_INFO_PLIST_RELEASE` | Base64-encoded production `GoogleService-Info.plist`; CI decodes it to `iosApp/Firebase/Prod/GoogleService-Info.plist`. |
| `ASC_API_KEY_ID` | App Store Connect API key ID. |
| `ASC_API_ISSUER_ID` | App Store Connect API issuer UUID. |
| `ASC_API_KEY_BASE64` | Base64-encoded private key file `AuthKey_<key-id>.p8`. |
| `ASC_TEAM_ID` | Optional Apple Developer team ID; the release script defaults to `92SLHZAN8L`. |

Encode files without line wrapping and pipe values to `gh` through stdin:

```bash
base64 < iosApp/Firebase/Prod/GoogleService-Info.plist \
  | gh secret set GOOGLE_SERVICE_INFO_PLIST_RELEASE
```

```bash
base64 < AuthKey_ABCDE12345.p8 \
  | gh secret set ASC_API_KEY_BASE64
```

Set the scalar values separately:

```bash
gh secret set ASC_API_KEY_ID
gh secret set ASC_API_ISSUER_ID
```

Set `ASC_TEAM_ID` only when the signing team differs from the script default.
The API key is decoded into the runner's temporary directory. An `always()`
cleanup removes both that file and the copy staged for Apple tooling.

## App Store Connect API key

In App Store Connect:

1. Open **Users and Access → Integrations → App Store Connect API**.
2. Create a team API key with permission to upload builds (App Manager is
   sufficient).
3. Download the `.p8` file immediately; Apple exposes it only once.
4. Store its key ID, issuer ID, and base64-encoded file in the three `ASC_*`
   GitHub secrets above.

The key must belong to the same App Store Connect team as the MoneySurfer app.
The release script supplies it to Xcode for automatic signing and to `altool`
for the upload.

## Build number and artifacts

CI passes `github.run_number` as `ASC_BUILD_NUMBER`. The release script forwards
it to `xcodebuild archive` as `APP_VERSION_CODE=<number>`, which resolves
`CURRENT_PROJECT_VERSION` without editing
[`Version.xcconfig`](../../Version.xcconfig). TestFlight rejects duplicate build
numbers, so rerunning an existing workflow run may require starting a new run.

After a successful export, the workflow uploads the IPA to TestFlight and also
stores it as the `iosApp-release-ipa` GitHub Actions artifact for 14 days.
Apple processes TestFlight uploads asynchronously; a successful workflow means
the upload completed, not that processing or tester availability has finished.

## Relation to local releases

CI uses the same implementation as local releases:

```bash
scripts/ios/release.sh main
```

Locally, configure `ASC_API_KEY_ID`, `ASC_API_ISSUER_ID`,
`ASC_API_KEY_PATH`, and optionally `ASC_TEAM_ID` / `ASC_BUILD_NUMBER` as
environment variables or in gitignored `local.properties`. Environment
variables win. Use `scripts/ios/release.sh main --no-upload` to archive and
export without App Store Connect credentials.

The local script also supports `offline` and `all`; those modes are outside the
online-only GitHub Actions workflow.
<!-- AI:END -->
