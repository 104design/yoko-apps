#!/usr/bin/env bash
# よこめぐりのAPKを作る。news-widget/build.sh と同じ流れ。
#
# 道具は次のどちらかを使う。
#   1. Android SDK（ANDROID_SDK_ROOT、なければ ~/workspace/tools/android-sdk）の build-tools 34.0.0
#   2. SDKがなければ、PATH上の aapt2・zipalign・apksigner・dalvik-exchange
#      （Ubuntuなら apt install aapt zipalign apksigner dalvik-exchange）。
#      この場合は ANDROID_JAR に android.jar 相当のjarを指定する。
#
# 署名の鍵は debug.keystore（このフォルダ）。なければ ../pomodoro-timer/debug.keystore、
# それもなければ新しく作る。鍵が変わると端末のアプリを上書き更新できない。
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_SDK_ROOT:-$HOME/workspace/tools/android-sdk}"
BT="$SDK/build-tools/34.0.0"
APP=app/src/main
OUT=build
APK="$OUT/yokomeguri.apk"

if [ -x "$BT/aapt2" ]; then
  AAPT2="$BT/aapt2"; ZIPALIGN="$BT/zipalign"; APKSIGNER="$BT/apksigner"; DEXER="d8"
  ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-34/android.jar}"
else
  AAPT2=aapt2; ZIPALIGN=zipalign; APKSIGNER=apksigner; DEXER="dx"
  : "${ANDROID_JAR:?SDKが見つかりません。ANDROID_JAR に android.jar を指定してください}"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile =="
"$AAPT2" compile --dir "$APP/res" -o "$OUT/compiled.zip"

echo "== aapt2 link =="
"$AAPT2" link -o "$OUT/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$OUT/compiled.zip" \
  --java "$OUT/gen" \
  --auto-add-overlay

echo "== javac =="
find "$APP/java" "$OUT/gen" -name "*.java" > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
  -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "== dex ($DEXER) =="
if [ "$DEXER" = "d8" ]; then
  "$BT/d8" --lib "$ANDROID_JAR" --min-api 26 \
    --output "$OUT/dex" $(find "$OUT/classes" -name "*.class")
else
  dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

echo "== package dex =="
cp "$OUT/app-unsigned.apk" "$OUT/app-unsigned2.apk"
(cd "$OUT/dex" && zip -q "../app-unsigned2.apk" classes.dex)

echo "== keystore =="
if [ -f debug.keystore ]; then
  KS=debug.keystore
elif [ -f ../pomodoro-timer/debug.keystore ]; then
  KS=../pomodoro-timer/debug.keystore
else
  KS=debug.keystore
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  echo "新しい鍵を作りました: $KS（なくさないこと）"
fi
echo "鍵: $KS"

echo "== zipalign + sign =="
"$ZIPALIGN" -f 4 "$OUT/app-unsigned2.apk" "$OUT/app-aligned.apk"
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$APK" "$OUT/app-aligned.apk"

echo "== verify =="
"$APKSIGNER" verify --verbose "$APK" | head -4
ls -lh "$APK"
echo "DONE: $APK"
