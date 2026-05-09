#!/bin/sh
# Copy the per-configuration GoogleService-Info.plist into the built .app.
#
# iOS has no "product flavor" notion, so dev vs prod is encoded as
# build configurations: Debug = .dev bundle id, Release = prod bundle id.
# Each configuration needs its own Firebase project, hence its own plist.
#
# Run from a PBXShellScriptBuildPhase in iosApp.xcodeproj. Must run
# BEFORE the Resources phase (so Crashlytics dSYM upload at the end
# can read GOOGLE_APP_ID from the plist embedded in the .app).
#
# Inputs:  iosApp/Firebase/{Dev,Prod}/GoogleService-Info.plist
# Output:  $TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/GoogleService-Info.plist
#
# Both files are gitignored — download them per bundle id from the
# Firebase Console and place them locally.

set -euo pipefail

# Pick the source plist by Xcode build configuration.
case "$CONFIGURATION" in
  Debug)
    SRC="$SRCROOT/Firebase/Dev/GoogleService-Info.plist"
    ;;
  Release)
    SRC="$SRCROOT/Firebase/Prod/GoogleService-Info.plist"
    ;;
  *)
    # Custom configurations (e.g. Profile, custom CI configs) are not wired up.
    # Don't fail the build — just skip and let the developer notice if Firebase
    # is actually needed for that configuration.
    echo "warning: unknown CONFIGURATION=$CONFIGURATION, skipping Firebase plist copy"
    exit 0
    ;;
esac

if [ ! -f "$SRC" ]; then
  # Fail loud rather than silently shipping an .app without Firebase config —
  # otherwise FirebaseApp.configure() would crash at runtime with a confusing message.
  echo "error: Firebase plist missing at $SRC"
  echo "  Download it from https://console.firebase.google.com for the matching bundle id."
  exit 1
fi

# Use TARGET_BUILD_DIR (not BUILT_PRODUCTS_DIR) to match outputPaths declared
# on the build phase and the input path the Crashlytics upload phase reads.
# For a normal app target they are identical, but they can diverge for
# extensions/test bundles — keep them aligned to be safe.
DST="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/GoogleService-Info.plist"
mkdir -p "$(dirname "$DST")"
cp "$SRC" "$DST"
