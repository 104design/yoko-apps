#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-$HOME/workspace/tools/android-sdk}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
APP=app/src/main
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/compiled.zip"

echo "== aapt2 link =="
"$BT/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$OUT/compiled.zip" \
  --java "$OUT/gen" \
  --auto-add-overlay

echo "== javac =="
find "$APP/java" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 \
  -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "== d8 =="
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 \
  --output "$OUT/dex" $(find "$OUT/classes" -name "*.class")

echo "== package dex =="
cp "$OUT/app-unsigned.apk" "$OUT/app-unsigned2.apk"
cd "$OUT/dex"
zip -q "../app-unsigned2.apk" classes.dex
cd ../..

echo "== keystore (debug, local only) =="
if [ ! -f "$OUT/debug.keystore" ] && [ ! -f debug.keystore ]; then
  keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
KS=debug.keystore

echo "== zipalign + sign =="
"$BT/zipalign" -f 4 "$OUT/app-unsigned2.apk" "$OUT/app-aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/yokotimer.apk" "$OUT/app-aligned.apk"

echo "== verify =="
"$BT/apksigner" verify --verbose "$OUT/yokotimer.apk" | head -5
"$BT/aapt2" dump badging "$OUT/yokotimer.apk" | head -8
ls -lh "$OUT/yokotimer.apk"
echo "DONE: $OUT/yokotimer.apk"
