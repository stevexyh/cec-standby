#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
ANDROID="$SDK/platforms/android-36/android.jar"
OUT="$PWD/build"
rm -rf "$OUT/classes" "$OUT/dex"
mkdir -p "$OUT/classes" "$OUT/dex"
if [ -n "${CEC_KEYSTORE:-}" ]; then
    : "${CEC_KEY_ALIAS:?Set CEC_KEY_ALIAS}" "${CEC_STORE_PASS:?Set CEC_STORE_PASS}" "${CEC_KEY_PASS:?Set CEC_KEY_PASS}"
else
    mkdir -p .keys
    chmod 700 .keys
    CEC_KEYSTORE="$PWD/.keys/development.jks"
    CEC_KEY_ALIAS=development
    CEC_STORE_PASS=android
    CEC_KEY_PASS=android
    export CEC_STORE_PASS CEC_KEY_PASS
    if [ ! -f "$CEC_KEYSTORE" ]; then
        keytool -genkeypair -keystore "$CEC_KEYSTORE" -alias "$CEC_KEY_ALIAS" -storepass:env CEC_STORE_PASS -keypass:env CEC_KEY_PASS -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=CEC Standby Development'
        chmod 600 "$CEC_KEYSTORE"
    fi
fi
export CEC_STORE_PASS CEC_KEY_PASS
"$BT/aapt" package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID" -F "$OUT/unsigned.apk"
find src -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$ANDROID" -classpath "$BT/core-lambda-stubs.jar" -d "$OUT/classes" @"$OUT/sources.txt"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --release --min-api 28 --lib "$ANDROID" --output "$OUT/dex" "$OUT/classes.jar"
(cd "$OUT/dex" && "$BT/aapt" add "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$CEC_KEYSTORE" --ks-key-alias "$CEC_KEY_ALIAS" --ks-pass env:CEC_STORE_PASS --key-pass env:CEC_KEY_PASS --out "$OUT/cec-standby.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/cec-standby.apk"
echo "$OUT/cec-standby.apk"
