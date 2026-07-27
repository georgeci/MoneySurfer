# TestFlight distribution (iOS online)

<!-- DOCS:TOC -->
## Contents
- [TestFlight distribution (iOS online)](#testflight-distribution-ios-online)
- [TL;DR for agents](#tldr-for-agents)
- [Scope and triggers](#scope-and-triggers)
- [Required secrets](#required-secrets)
- [App Store Connect API key](#app-store-connect-api-key)
- [Distribution signing assets](#distribution-signing-assets)
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
| `IOS_DISTRIBUTION_CERTIFICATE_BASE64` | Base64-encoded password-protected `.p12` containing the Apple Distribution certificate and its private key. |
| `IOS_DISTRIBUTION_CERTIFICATE_PASSWORD` | Password used when exporting the `.p12`. |
| `IOS_PROVISIONING_PROFILE_BASE64` | Base64-encoded App Store distribution `.mobileprovision` for `com.georgeci.moneysurfer`. |
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

```bash
base64 < MoneySurferDistribution.p12 \
  | gh secret set IOS_DISTRIBUTION_CERTIFICATE_BASE64
base64 < MoneySurfer_AppStore.mobileprovision \
  | gh secret set IOS_PROVISIONING_PROFILE_BASE64
```

Set the scalar values separately:

```bash
gh secret set ASC_API_KEY_ID
gh secret set ASC_API_ISSUER_ID
gh secret set IOS_DISTRIBUTION_CERTIFICATE_PASSWORD
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

## Distribution signing assets

GitHub-hosted macOS runners do not contain the project's signing identity.
Create or renew the assets under Apple Developer **Certificates, Identifiers &
Profiles**:

1. Under **Certificates**, create an **Apple Distribution** certificate.
   Install it on a trusted Mac, then export the certificate together with its
   private key from Keychain Access as a password-protected `.p12`.
2. Under **Profiles**, create an **App Store Connect** distribution profile for
   the explicit App ID `com.georgeci.moneysurfer`, select the same distribution
   certificate, and download the `.mobileprovision`.
3. Base64-encode both files and store them with the certificate export password
   in the three `IOS_*` repository secrets above.

The workflow creates a temporary keychain, imports the `.p12`, and installs the
profile in the runner's provisioning-profile directory before calling the
release script. It extracts the profile's `Name` and passes it as
`IOS_PROVISIONING_PROFILE_NAME`; the release script then exports with manual
signing, the installed **Apple Distribution** identity, and that exact profile
for `com.georgeci.moneysurfer`. This avoids cloud signing during export. The
workflow's `always()` cleanup deletes the temporary keychain and installed
profile, including after archive or upload failure. Rotate the certificate and
profile secrets together when either asset expires or is revoked.

## Build number and artifacts

CI passes `github.run_number` as one `ASC_BUILD_NUMBER`. The release script
forwards that value to `xcodebuild archive` as both:

- `APP_BUILD_NUMBER=<number>`, producing the marketing version
  `major.minor.build`.
- `APP_VERSION_CODE=<number>`, producing the iOS `CFBundleVersion`.

Both values are build-setting overrides, so
[`Version.xcconfig`](../../Version.xcconfig) and the working tree remain
unchanged. Each new workflow run gets a new `github.run_number`; rerunning the
same run reuses its number. TestFlight rejects an already-uploaded
`CFBundleVersion`, so use a new workflow run when retrying after a successful
upload.

GitHub maintains `run_number` per workflow. Android and iOS distribution
therefore have separate counters; matching build suffixes across the two
platforms are not expected.

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
