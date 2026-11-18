#!/usr/bin/env bash
# Build AI Mobile Motions APK without Gradle. CI runs this too.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/build"
ANDROID_JAR="${ANDROID_JAR:-/tmp/amm_build/platform/android-13/android.jar}"
R8_JAR="${R8_JAR:-/tmp/amm_build/r8.jar}"
BT="${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/29.0.3}"
rm -rf "$BUILD"; mkdir -p "$BUILD/res" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

"$BT/aapt2" compile --dir "$APP/src/main/res" -o "$BUILD/res/compiled.zip"
"$BT/aapt2" link -o "$BUILD/base.apk" -I "$ANDROID_JAR" \
  --manifest "$APP/src/main/AndroidManifest.xml" \
  -R "$BUILD/res/compiled.zip" --java "$BUILD/gen" \
  --min-sdk-version 26 --target-sdk-version 33 --version-code 1 --version-name 1.0 \
  --auto-add-overlay -A "$APP/src/main/assets"

find "$APP/src/main/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -nowarn -source 8 -target 8 -cp "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"
java -cp "$R8_JAR" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 26 \
  --output "$BUILD/dex" $(find "$BUILD/classes" -name '*.class')
( cd "$BUILD/dex" && zip -q -u "$BUILD/base.apk" classes.dex )

"$BT/zipalign" -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"
if [ ! -f "$ROOT/debug.jks" ]; then
  keytool -genkeypair -keystore "$ROOT/debug.jks" -storepass android -keypass android \
    -alias amm -dname "CN=AI Mobile Motions" -validity 10000 -keyalg RSA >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$ROOT/debug.jks" --ks-pass pass:android \
  --key-pass pass:android --out "$ROOT/ai-mobile-motions.apk" "$BUILD/aligned.apk"
echo "APK: $ROOT/ai-mobile-motions.apk ($(du -h "$ROOT/ai-mobile-motions.apk" | cut -f1))"
