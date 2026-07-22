# Firebase App Distribution (android-online)

<!-- DOCS:TOC -->
## Contents
- [Firebase App Distribution (android-online)](#firebase-app-distribution-android-online)
- [Triggers](#triggers)
- [Required secrets](#required-secrets)
- [Service account](#service-account)
- [Testers](#testers)
- [Notes on the build](#notes-on-the-build)
<!-- DOCS:END -->

[`.github/workflows/android-distribute.yml`](../../.github/workflows/android-distribute.yml)
builds the signed release APK of `:androidApp` and uploads it to Firebase App
Distribution.

Scope: **android-online only**. `:androidApp-offline` is Firebase-free by
design, and iOS testers go through TestFlight
([`scripts/ios/release.sh`](../../scripts/ios/release.sh)).

## Triggers

| Trigger             | When                                                       |
| ------------------- | ---------------------------------------------------------- |
| `workflow_dispatch` | The "Run workflow" button. Inputs: `groups`, `notes`.       |
| `schedule`          | 03:47 UTC daily — 30 min after `nightly.yml`'s 03:17 slot.  |

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

- `versionCode` / `versionName` come from `Version.xcconfig`, so consecutive
  nightlies share a version. App Distribution allows this — each upload is a
  separate release.
- The release build is minified/shrunk (R8). Crashlytics mapping and native
  symbol uploads are separate Gradle tasks and are **not** run here.
- The APK is also kept as a run artifact (`androidApp-release-apk`, 14 days).
