# Firebase App Distribution (Android)

<!-- DOCS:TOC -->
## Contents
- [Firebase App Distribution (Android)](#firebase-app-distribution-android)
- [Triggers](#triggers)
- [Required secrets](#required-secrets)
- [Offline Firebase app registration](#offline-firebase-app-registration)
- [Service account](#service-account)
- [Testers](#testers)
- [Notes on the build](#notes-on-the-build)
<!-- DOCS:END -->

The distribution workflows build signed release APKs and upload them to
Firebase App Distribution:

- [`android-distribute.yml`](../../.github/workflows/android-distribute.yml):
  `:androidApp` (online).
- [`android-offline-distribute.yml`](../../.github/workflows/android-offline-distribute.yml):
  `:androidApp-offline`.

The offline app remains Firebase-free. Firebase App Distribution is an external
delivery channel; the offline APK does not package Firebase SDKs or
`google-services.json`. iOS testers go through TestFlight
([iOS TestFlight distribution](testflight.md)).

## Triggers

| Trigger             | When                                                       |
| ------------------- | ---------------------------------------------------------- |
| `workflow_dispatch` | The "Run workflow" button. Inputs: `groups`, `notes`.       |
| `schedule`          | Online: 03:47 UTC. Offline: 04:07 UTC.                      |

The scheduled run is skipped when `main` hasn't moved since the last successful
distribute run (same `gh run list` check `nightly.yml` uses), so an idle repo
doesn't spam testers with identical builds.

The button only appears once the workflow file is on the default branch.

## Required secrets

| Secret                           | Contents                                                     |
| -------------------------------- | ------------------------------------------------------------ |
| `GOOGLE_SERVICES_JSON_RELEASE`   | base64 of `androidApp/src/release/google-services.json` (already set — used by `nightly.yml`) |
| `ANDROID_RELEASE_KEYSTORE_BASE64`| base64 of the release keystore (`.jks`)                       |
| `ANDROID_RELEASE_STORE_PASSWORD` | keystore password                                             |
| `ANDROID_RELEASE_KEY_ALIAS`      | key alias                                                     |
| `ANDROID_RELEASE_KEY_PASSWORD`   | key password                                                  |
| `FIREBASE_SERVICE_ACCOUNT_JSON`  | inline service-account JSON with **Firebase App Distribution Admin** |

The offline workflow also requires the repository variable
`FIREBASE_ANDROID_OFFLINE_APP_ID`, containing the Firebase Android app ID for
package `com.georgeci.moneysurfer.offline`.

The keystore secrets map 1:1 onto the `RELEASE_*` env vars
[`KmpAppConventionPlugin`](../../build-logic/kmp/src/main/kotlin/com/georgeci/moneysurfer/buildlogic/KmpAppConventionPlugin.kt)
reads. Without all four the plugin silently drops the `release` signingConfig
and produces an unsigned APK — the workflow catches that with `apksigner verify`
before it reaches Firebase.

Upload them (values are read from stdin, never from argv):

```bash
base64 < keystore/release.jks | gh secret set ANDROID_RELEASE_KEYSTORE_BASE64
```

```bash
gh secret set FIREBASE_SERVICE_ACCOUNT_JSON < path/to/service-account.json
```

The Firebase **app id** is not a secret: the workflow reads
`mobilesdk_app_id` for `com.georgeci.moneysurfer` out of the decoded
`google-services.json`.

## Offline Firebase app registration

In Firebase console, add a second Android app with package name
`com.georgeci.moneysurfer.offline`. Copy its App ID (the
`1:...:android:...` value) into the GitHub Actions repository variable:

```bash
gh variable set FIREBASE_ANDROID_OFFLINE_APP_ID --body '1:...:android:...'
```

Do not download or add its `google-services.json` to the repository. The
offline workflow passes the App ID directly to Firebase CLI, preserving the
offline binary's no-Firebase and no-network guarantees.

## Service account

In the Google Cloud console for the Firebase project:

1. Create a service account (e.g. `github-app-distribution`).
2. Grant it the **Firebase App Distribution Admin** role.
3. Create a JSON key and store it as `FIREBASE_SERVICE_ACCOUNT_JSON`.

The Firebase CLI (pinned by
[`.github/actions/firebase-tools`](../../.github/actions/firebase-tools)) picks
the key up via `GOOGLE_APPLICATION_CREDENTIALS`. Both the key and the decoded
keystore live in `$RUNNER_TEMP` and are removed in an `always()` step.

## Testers

Uploads target the tester **group alias** — `testers` by default, overridable
per manual run. Create the group in the Firebase console under
App Distribution → Testers & Groups; the alias (not the display name) is what
the workflow passes to `--groups`.

A non-existent alias is a hard error, and the groups API answers `404` until
App Distribution has been opened once for the project. Bootstrap path: run the
workflow manually with an **empty** `groups` input — the release is uploaded but
not handed to anyone — then create the group in the console and run again.

Scheduled runs always use `testers` and fail loudly if it doesn't exist. That is
deliberate: a nightly that quietly uploads to nobody looks identical to a
working one, and testers would notice the gap long after the misconfiguration.

## Notes on the build

- The version is `major.minor.build`. `major` / `minor` are edited by hand in
  [`Version.xcconfig`](../../Version.xcconfig). Each workflow uses its own
  independent `github.run_number` as `build`; online and offline are different
  application IDs, so their sequences do not need to match. Local builds use
  the resettable `APP_BUILD_NUMBER` default from the file (`0`).
- `versionCode` is derived as `major * 100000 + minor * 1000 + build` — two
  digits for minor, three for the build. The build number is taken `mod 1000`
  so it can never carry into `minor`; the build fails only on a hand-edited
  `major > 20999` (Play's own ceiling), `minor > 99`, or an `APP_BUILD_NUMBER`
  override that isn't a number (a truncated CI value must not silently reuse
  the default and stamp two commits with the same version).
- The wrap is worth knowing about: when the supplied build number reaches a
  multiple of 1000 it becomes build `0`, i.e. a **lower** `versionCode` than
  the previous build. App Distribution doesn't care, but Play rejects a
  non-increasing code — bump `APP_VERSION_MINOR` before the counter wraps if
  the same versioning ever feeds a Play upload.
- The release build is minified/shrunk (R8). Crashlytics mapping and native
  symbol uploads are separate Gradle tasks and are **not** run here.
- APKs are also kept as run artifacts for 14 days:
  `androidApp-release-apk` and `androidApp-offline-release-apk`.
