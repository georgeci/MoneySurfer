
<!-- DOCS:TOC -->
## Contents
- [Release pipeline via GitHub Actions](#release-pipeline-via-github-actions)
- [Context](#context)
- [Phasing](#phasing)
- [A. Code changes (outside the workflow)](#a-code-changes-outside-the-workflow)
  - [A1. composeApp/build.gradle.kts — sync Desktop version](#a1-composeappbuildgradlekts--sync-desktop-version)
  - [A2. iosApp/ExportOptions.plist (new)](#a2-iosappexportoptionsplist-new)
  - [A3. secretOrEnv (androidApp/build.gradle.kts:137-140)](#a3-secretorenv-androidappbuildgradlekts137-140)
- [B. .github/workflows/release.yml (new)](#b-githubworkflowsreleaseyml-new)
- [C. GitHub repository secrets](#c-github-repository-secrets)
- [D. Critical files](#d-critical-files)
- [E. Risks and gotchas](#e-risks-and-gotchas)
- [F. Verification](#f-verification)
<!-- DOCS:END -->

---
title: Release pipeline via GitHub Actions
created: 2026-05-06
status: backlog
---

# Release pipeline via GitHub Actions

## Context

`money-surfer-2026` is an early-stage KMP app (Android + iOS + Desktop JVM). `Version.xcconfig` currently has `APP_VERSION_CODE=2`, `APP_VERSION_NAME=0.0.2`. `.github/workflows/` already contains `ci.yml` (running `qaCommon` tests), `codeql.yml`, and `nightly.yml`, but there is no tag-driven release workflow. Every build is produced by hand, which slows down beta cadence and increases the risk of mistakes (version drift, forgotten signing, lost artifacts).

**Goal**: when a `vX.Y.Z` tag is pushed, automatically build release artifacts for all three platforms, publish Android to Play Console internal track, push iOS to TestFlight, and attach every binary to a GitHub Release.

**Recommendation**: yes — but roll out in phases. The project already has the foundation (Android signing via env variables, iOS reads version from a shared `Version.xcconfig`), so Phase 1 (Desktop + GitHub Release) ships with almost no new secrets. Phase 2/3 plug in independently.

## Phasing

| Phase | Scope | New secrets | When to ship |
|---|---|---|---|
| **1** | `validate-tag` + Desktop matrix + GitHub Release | none (just `GITHUB_TOKEN`) | now |
| **2** | Android job → Play internal | keystore + Play SA + google-services.json | once a Play Console listing exists |
| **3** | iOS job → TestFlight | dist cert + provisioning + ASC API key + plist | once an App Store Connect record exists |

## A. Code changes (outside the workflow)

### A1. `composeApp/build.gradle.kts` — sync Desktop version

the `nativeDistributions { ... packageVersion = "1.0.0" }` block in `composeApp/build.gradle.kts` currently hard-codes `packageVersion = "1.0.0"`. That drifts away from Android/iOS, which read `Version.xcconfig`.

Add at the top of the file (next to the other `import`s):

```kotlin
import java.util.Properties

val versionProperties = Properties().apply {
    rootProject.file("Version.xcconfig").inputStream().use { load(it) }
}
```

Replace the `nativeDistributions { ... packageVersion = "1.0.0" }` block in `composeApp/build.gradle.kts`:

```kotlin
packageVersion = versionProperties.getProperty("APP_VERSION_NAME").trim()
```

`.trim()` is required — `Version.xcconfig` has whitespace after the `=`.

### A2. `iosApp/ExportOptions.plist` (new)

Needed for `xcodebuild -exportArchive`. `teamID` is substituted via `envsubst` from the `APPLE_TEAM_ID` secret — never committed verbatim.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>app-store</string>
  <key>teamID</key><string>${APPLE_TEAM_ID}</string>
  <key>signingStyle</key><string>manual</string>
  <key>uploadBitcode</key><false/>
  <key>uploadSymbols</key><true/>
  <key>provisioningProfiles</key>
  <dict>
    <key>com.georgeci.moneysurfer</key>
    <string>MoneySurfer App Store</string>
  </dict>
</dict>
</plist>
```

The profile name (`MoneySurfer App Store`) must match the `Name` key inside the `.mobileprovision`.

### A3. `secretOrEnv` (`androidApp/build.gradle.kts:137-140`)

No change required. The helper already reads `local.properties` → env vars correctly; CI just exports the variables from secrets.

## B. `.github/workflows/release.yml` (new)

Structure (pseudo-YAML):

```yaml
on:
  push: { tags: ['v*.*.*'] }
  workflow_dispatch: { inputs: { tag: { required: true } } }

permissions:
  contents: write   # for action-gh-release

jobs:
  validate-tag:                                         # ubuntu-latest
    # Resolve the tag uniformly across triggers:
    #   - push:             TAG_REF=${{ github.ref_name }}     (e.g. "v0.0.3")
    #   - workflow_dispatch: TAG_REF=${{ inputs.tag }}
    # All downstream jobs use `actions/checkout` with `ref: refs/tags/${{ needs.validate-tag.outputs.tag }}`
    # so workflow_dispatch runs build the tagged commit, not the default branch.
    - checkout: ref: refs/tags/${{ inputs.tag || github.ref_name }}
    - parse APP_VERSION_NAME from Version.xcconfig:
        grep '^APP_VERSION_NAME' Version.xcconfig | cut -d= -f2 | xargs
    - assert "v$NAME" == "$TAG_REF"; otherwise fail
    - outputs: tag (resolved), version_name

  desktop:                                              # Phase 1
    needs: validate-tag
    strategy: { matrix: { os: [macos-latest, windows-latest, ubuntu-latest] } }
    runs-on: ${{ matrix.os }}
    steps:
      - checkout, setup-java 17, setup-gradle
      - ./gradlew :composeApp:packageReleaseDistributionForCurrentOS
      - upload-artifact: composeApp/build/compose/binaries/main-release/{dmg,msi,deb}/*

  android:                                              # Phase 2
    needs: validate-tag
    runs-on: ubuntu-latest
    env:
      RELEASE_STORE_FILE: ${{ runner.temp }}/release.jks
      RELEASE_STORE_PASSWORD: ${{ secrets.RELEASE_STORE_PASSWORD }}
      RELEASE_KEY_ALIAS: ${{ secrets.RELEASE_KEY_ALIAS }}
      RELEASE_KEY_PASSWORD: ${{ secrets.RELEASE_KEY_PASSWORD }}
    steps:
      - checkout, setup-java 17, setup-gradle
      - echo "$RELEASE_KEYSTORE_BASE64" | base64 -d > $RELEASE_STORE_FILE
      - echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 -d > androidApp/google-services.json
      - ./gradlew :androidApp:bundleRelease :androidApp:assembleRelease
      - apksigner verify --print-certs <apk>     # fail fast if debug-signed (APK only)
      - jarsigner -verify -strict <aab>          # AAB signature check (apksigner doesn't support .aab)
      - r0adkll/upload-google-play@v1
          serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.georgeci.moneysurfer
          releaseFiles: androidApp/build/outputs/bundle/release/*.aab
          track: internal
      - upload-artifact: aab + apk

  ios:                                                  # Phase 3
    needs: validate-tag
    runs-on: macos-latest
    env:
      APPLE_TEAM_ID: ${{ secrets.APPLE_TEAM_ID }}
    steps:
      - checkout
      - apple-actions/import-codesign-certs@v3
          p12-file-base64: ${{ secrets.IOS_DIST_CERTIFICATE_P12_BASE64 }}
          p12-password: ${{ secrets.IOS_DIST_CERTIFICATE_PASSWORD }}
      - mkdir -p ~/Library/MobileDevice/Provisioning\ Profiles
      - echo "$IOS_PROVISIONING_PROFILE_BASE64" | base64 -d \
          > ~/Library/MobileDevice/Provisioning\ Profiles/dist.mobileprovision
      - echo "$IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64" | base64 -d \
          > iosApp/iosApp/GoogleService-Info.plist
      - envsubst < iosApp/ExportOptions.plist > $RUNNER_TEMP/ExportOptions.plist
      - xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
          -configuration Release -archivePath build/iosApp.xcarchive archive
      - xcodebuild -exportArchive -archivePath build/iosApp.xcarchive \
          -exportPath build/ipa -exportOptionsPlist $RUNNER_TEMP/ExportOptions.plist
      - apple-actions/upload-testflight-build@v3
          app-path: build/ipa/iosApp.ipa
          issuer-id: ${{ secrets.APPSTORE_API_ISSUER_ID }}
          api-key-id: ${{ secrets.APPSTORE_API_KEY_ID }}
          api-private-key: ${{ secrets.APPSTORE_API_PRIVATE_KEY }}
      - upload-artifact: *.ipa

  github-release:
    needs: [validate-tag, desktop]    # add android/ios as they come online
    runs-on: ubuntu-latest
    steps:
      - download-artifact (all)
      - softprops/action-gh-release@v2
          # Use the resolved tag from validate-tag so both push and workflow_dispatch
          # publish under the correct tag (github.ref_name = branch on workflow_dispatch).
          tag_name: ${{ needs.validate-tag.outputs.tag }}
          generate_release_notes: true
          files: |
            **/*.aab
            **/*.apk
            **/*.ipa
            **/*.dmg
            **/*.msi
            **/*.deb
```

## C. GitHub repository secrets

**Phase 2 (Android)**
- `RELEASE_KEYSTORE_BASE64` — `base64 release.jks`
- `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
- `GOOGLE_SERVICES_JSON_BASE64` — Firebase Android config (lands in `androidApp/google-services.json`)
- `PLAY_SERVICE_ACCOUNT_JSON` — Play Console service account JSON (plain, not base64)

**Phase 3 (iOS)**
- `IOS_DIST_CERTIFICATE_P12_BASE64`, `IOS_DIST_CERTIFICATE_PASSWORD` — distribution cert
- `IOS_PROVISIONING_PROFILE_BASE64` — `.mobileprovision` for `com.georgeci.moneysurfer`
- `APPLE_TEAM_ID` — 10-character team ID
- `APPSTORE_API_KEY_ID`, `APPSTORE_API_ISSUER_ID`, `APPSTORE_API_PRIVATE_KEY` — App Store Connect API key (.p8)
- `IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64` — Firebase iOS config

## D. Critical files

- `.github/workflows/release.yml` — **new**
- `composeApp/build.gradle.kts` — fix the hard-coded `packageVersion` inside the `nativeDistributions` block
- `iosApp/ExportOptions.plist` — **new** (Phase 3)
- `Version.xcconfig` — bump `APP_VERSION_NAME` before each tag
- `androidApp/build.gradle.kts:14-19,62-77` — `hasReleaseSigning` conditional block: confirm it actually activates under CI env variables

## E. Risks and gotchas

1. **`hasReleaseSigning` (`androidApp/build.gradle.kts:14-19`)**: if any of the four env variables is missing, the release signingConfig is not created and `bundleRelease` silently falls back to debug signing → Play rejects with `INVALID_APK_SIGNATURE`. **Mitigation**: verify the APK with `apksigner verify --print-certs` and the AAB with `jarsigner -verify -strict` before `upload-google-play` (apksigner only supports APKs).
2. **Compose Desktop `packageVersion` validation**: jpackage requires strict semver `X.Y.Z`. `0.0.2` is valid; suffixes like `0.0.2-beta` are not allowed.
3. **iOS provisioning profile**: must be issued under team `APPLE_TEAM_ID` and bundle id `com.georgeci.moneysurfer` (see `iosApp.xcodeproj`). Mismatches are the #1 cause of `exportArchive` failure.
4. **`google-services.json` placement**: must live at `androidApp/google-services.json` (module root), not under `src/main/`.
5. **TestFlight is async**: `upload-testflight-build` returns once the upload completes, not after Apple finishes processing. Don't block `github-release` on it.
6. **macOS .dmg without notarization**: `packageReleaseDistributionForCurrentOS` produces an unsigned/unnotarized .dmg → users will hit a Gatekeeper warning. Acceptable for 0.0.x; resolve before 1.0.
7. **Version from xcconfig has leading whitespace**: always apply `.trim()` / `xargs` when reading it.

## F. Verification

1. **Lint YAML locally**: `actionlint .github/workflows/release.yml` before pushing.
2. **Phase 1 smoke test**:
   - Bump `APP_VERSION_NAME` to `0.0.3` in `Version.xcconfig` and merge to main.
   - Create and push a real test tag matching the version: `git tag v0.0.3-rc1 && git push origin v0.0.3-rc1`. (`validate-tag` checks out `refs/tags/...`, so the tag must exist on the remote.)
   - Note: the strict comparison `v$APP_VERSION_NAME == $TAG_REF` rejects suffixes. Either relax the check to `$TAG_REF == v$APP_VERSION_NAME*` for rc/beta tags, or bump `APP_VERSION_NAME` to exactly match the tag (e.g. `0.0.3-rc1`) for testing — pick one and document it on the workflow.
   - Confirm the Desktop matrix produces three artifacts (.dmg, .msi, .deb) and they show up in a draft GitHub Release.
   - Only then push the real tag: `git tag v0.0.3 && git push origin v0.0.3`.
3. **Tag-mismatch test**: push `v9.9.9` against `APP_VERSION_NAME=0.0.3` — `validate-tag` must fail and the rest of the jobs must skip.
4. **Phase 2**: after Android lands, verify via `workflow_dispatch` with a test tag that the AAB shows up in the Play internal track. The very first build must be uploaded manually to Play Console (Play requires manual app-signing-key bootstrap).
5. **Phase 3**: TestFlight upload is the most iterative piece. Use "Re-run failed jobs" to debug provisioning without recutting the tag.
6. **`act`** for ubuntu jobs: `act push -j desktop --matrix os:ubuntu-latest`. macOS/Windows runners are not supported by `act` — only real GH.
