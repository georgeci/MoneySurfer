# Offline build network guard

<!-- DOCS:TOC -->
## Contents
- [Offline build network guard](#offline-build-network-guard)
- [Android](#android)
- [iOS](#ios)
- [Tripwire test (manual regression check)](#tripwire-test-manual-regression-check)
- [Bypass](#bypass)
<!-- DOCS:END -->

The offline build (`androidApp-offline`, `iosAppOffline`) must never ship with
networking capability. A transitive AAR or a Pod could silently re-introduce
`android.permission.INTERNET` or a network entitlement, and we would only learn
about it via App Store review or a privacy complaint. The guards below fail the
build *before* a binary is ever produced.

## Android

- [androidApp-offline/src/main/AndroidManifest.xml](../../androidApp-offline/src/main/AndroidManifest.xml)
  declares `<uses-permission ... tools:node="remove" />` for `INTERNET`,
  `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`,
  `CHANGE_WIFI_STATE`. The manifest merger drops any matching permission added
  by a dependency.
- [androidApp-offline/build.gradle.kts](../../androidApp-offline/build.gradle.kts)
  registers a `verifyOfflineManifest<Variant>` task per Android variant that
  parses the merged manifest (via
  `androidComponents.onVariants { artifacts.get(SingleArtifact.MERGED_MANIFEST) }`)
  and fails if any forbidden permission survived. The task is wired into
  `assemble<Variant>`, `bundle<Variant>`, and `install<Variant>`, so:
  ```
  ./gradlew :androidApp-offline:assembleDebug
  ```
  is red on leak.

## iOS

- [scripts/verify-offline-ios-no-network.sh](../../scripts/verify-offline-ios-no-network.sh)
  is run as a build phase on the `iosAppOffline` target. It uses `PlistBuddy`
  to refuse the build if the resolved Info.plist contains
  `NSAppTransportSecurity`, `NSLocalNetworkUsageDescription`, or `NSBonjourServices`,
  or if the entitlements file contains any
  `com.apple.developer.networking.*` capability or `associated-domains`.
- The script also runs in standalone mode (no Xcode env), so CI can invoke it
  directly:
  ```
  ./scripts/verify-offline-ios-no-network.sh
  ```

## Tripwire test (manual regression check)

To prove the Android guard is wired correctly, temporarily introduce a
dependency that requires `INTERNET` and observe a red build:

1. Edit [androidApp-offline/build.gradle.kts](../../androidApp-offline/build.gradle.kts)
   and add `implementation("com.squareup.okhttp3:okhttp:4.12.0")`.
2. Run `./gradlew :androidApp-offline:assembleDebug`.
3. Expect failure from `verifyOfflineManifestDebug` listing
   `android.permission.INTERNET`. The error points to the merged manifest path
   so you can confirm the offending dependency from the merger report.
4. Revert the dependency change.

For iOS, add a `<key>NSAppTransportSecurity</key><dict/>` block to
[iosAppOffline/iosAppOffline/Info.plist](../../iosAppOffline/iosAppOffline/Info.plist),
build the `iosAppOffline` scheme, expect the `Verify Offline Build Has No
Network Capability` phase to fail, then revert.

## Bypass

There is no silent bypass. If a future feature genuinely needs network access in
the offline target (it should not), the guard must be explicitly relaxed in
this repo via a reviewed PR labelled `area:online`, not by deleting tasks or
silencing manifest entries.
