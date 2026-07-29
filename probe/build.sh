#!/bin/bash
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK=/Users/yuechen/Library/Android/sdk
BT=$SDK/build-tools/34.0.0
PLAT=$SDK/platforms/android-35/android.jar
MK=/Users/yuechen/home/octos-one/makepad/tools/cargo_makepad
JDK=$MK/android_33_macos_aarch64/openjdk
KS=$MK/debug.keystore
OUT=$HERE/out
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/apk/lib/arm64-v8a"

echo "== aapt2 link =="
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLAT" \
  --manifest "$HERE/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 30

echo "== javac =="
"$JDK/bin/javac" -source 1.8 -target 1.8 -Xlint:-options \
  -classpath "$PLAT" -d "$OUT/classes" \
  $(find "$HERE/java" -name '*.java') $(find "$OUT/gen" -name '*.java' 2>/dev/null)

echo "== d8 =="
"$JDK/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLAT" --min-api 26 --output "$OUT" \
  $(find "$OUT/classes" -name '*.class')

echo "== assemble =="
cp "$HERE/rust/target/aarch64-linux-android/release/libsplash_android_probe.so" \
   "$OUT/apk/lib/arm64-v8a/"
cd "$OUT"
cp base.apk probe.apk
cp classes.dex apk/ 2>/dev/null || true
cd "$OUT/apk" && "$JDK/bin/jar" 2>/dev/null || true
cd "$OUT"
# add dex at archive root and the .so under lib/
mkdir -p stage/lib/arm64-v8a
cp classes.dex stage/
cp apk/lib/arm64-v8a/libsplash_android_probe.so stage/lib/arm64-v8a/
cd stage && zip -q -r -X "$OUT/probe.apk" classes.dex lib && cd "$OUT"

echo "== align + sign =="
"$BT/zipalign" -f -p 4 probe.apk probe-aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out probe-signed.apk probe-aligned.apk
echo "APK: $OUT/probe-signed.apk"
