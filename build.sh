#!/bin/sh
# Zero-Gradle fast builder for Aayush OS (dev.aayush.os)
set -e
cd "$(dirname "$0")"

for j in "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" "/opt/homebrew/opt/openjdk@17" "/usr/local/opt/openjdk@17"; do
    [ -d "$j" ] && JAVA_HOME="$j" && break
done
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] || for d in "$HOME/.android-sdk" "/opt/homebrew/share/android-commandlinetools" "$HOME/Library/Android/sdk"; do
    [ -d "$d" ] && SDK="$d" && break
done

BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
[ -f "$JAR" ] && [ -d "$BT" ] || { echo "Android SDK not found at $SDK" >&2; exit 1; }

rm -rf out
mkdir -p out/classes out/dex out/gen

"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml \
    --java out/gen --min-sdk-version 29 --target-sdk-version 34 out/res.zip

# Compile all Java sources using Java 8 javac (native class version 52.0 for d8)
# shellcheck disable=SC2046
/usr/bin/javac -nowarn -Xlint:-options -encoding UTF-8 -source 1.8 -target 1.8 \
    -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" \
    -d out/classes $(find src out/gen -name "*.java")

# shellcheck disable=SC2046
"$BT/d8" --min-api 29 --release --lib "$JAR" --output out/dex $(find out/classes -name '*.class')

cp out/base.apk out/unsigned.apk
(cd out/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk

KS=${KEYSTORE:-aayush-os.jks}
PASS=${KEYSTORE_PASS:-aayush81}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" \
    -alias aayushos -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Aayush Mali, OU=AayushOS, O=McLaren81, C=IN" >/dev/null 2>&1

"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --out out/aayush-os.apk out/aligned.apk
echo "Built out/aayush-os.apk ($(du -h out/aayush-os.apk | cut -f1))"
