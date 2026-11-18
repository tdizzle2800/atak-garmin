#!/bin/bash
# Build the Foretrex plugin (civ debug) against YOUR copy of the ATAK-CIV 5.6.0 SDK.
# The SDK is not part of this repo. Download it from tak.gov and unzip it.
#
#   scripts/build.sh
#
# Optional environment variables:
#   ATAK_SDK        path to the unzipped SDK   (default: ~/Downloads/ATAK-CIV-5.6.0-SDK)
#   ATAK_MAIN_JAR   path to main.jar if it is not inside the SDK folder
#   ANDROID_SDK_ROOT / ANDROID_HOME   Android SDK (default: ~/Android/Sdk)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ATAK_SDK:-$HOME/Downloads/ATAK-CIV-5.6.0-SDK}"
ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
PROJ="$SDK/samples/atak-garmin"

die() { echo "ERROR: $*" >&2; exit 1; }

# 1. The SDK. The build plugin looks for these files two folders above the project.
[ -f "$SDK/atak-gradle-takdev.jar" ] || die "ATAK SDK not found at $SDK
  Download the ATAK-CIV 5.6.0 SDK from tak.gov, unzip it, then run:
    ATAK_SDK=/path/to/ATAK-CIV-5.6.0-SDK scripts/build.sh"
[ -f "$SDK/android_keystore" ] || die "$SDK/android_keystore is missing (incomplete SDK download?)"

if [ ! -f "$SDK/main.jar" ]; then
  CAND="${ATAK_MAIN_JAR:-$(dirname "$SDK")/sdk files/main.jar}"
  [ -f "$CAND" ] || die "main.jar is not in $SDK. Put it there, or set ATAK_MAIN_JAR=/path/to/main.jar"
  cp "$CAND" "$SDK/main.jar"
  echo "Copied main.jar into the SDK folder."
fi

# 2. JDK. Gradle 8.14 and the Android plugin need JDK 17 or 21 (JDK 25 fails with
#    "Unsupported class file major version 69").
jdk_major() { "$1/bin/java" -version 2>&1 | grep -m1 ' version "' | sed -E 's/.* version "([0-9]+)[".].*/\1/'; }
pick_jdk() {
  local h
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    case "$(jdk_major "$JAVA_HOME")" in 17|21) echo "$JAVA_HOME"; return;; esac
  fi
  for v in 21 17; do
    h=$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)
    [ -n "$h" ] && [ -x "$h/bin/java" ] && { echo "$h"; return; }
  done
  for h in "$HOME"/.gradle/jdks/*/Contents/Home \
           /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
           /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
           "/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    if [ -x "$h/bin/java" ]; then
      case "$(jdk_major "$h")" in 17|21) echo "$h"; return;; esac
    fi
  done
}
JDK="$(pick_jdk)"
[ -n "$JDK" ] || die "No JDK 17 or 21 found. Install one (macOS: brew install --cask temurin@21) and re-run."
export JAVA_HOME="$JDK"
echo "Using JDK: $JAVA_HOME"

[ -d "$ANDROID_SDK" ] || die "Android SDK not found at $ANDROID_SDK (Android Studio > Settings > Android SDK shows the path). Set ANDROID_SDK_ROOT."

# 3. The SDK's build plugin expects the project at <SDK>/samples/<project>, so copy the repo there.
mkdir -p "$PROJ"
(cd "$REPO" && tar -cf - \
    --exclude=.git --exclude=build --exclude=.gradle --exclude=.takdev --exclude=.idea \
    --exclude=local.properties --exclude=.DS_Store --exclude=dist .) | (cd "$PROJ" && tar -xf -)

# 4. local.properties: paths only, no credentials. The SDK's local jar is used instead of the tak.gov repo.
if [ ! -f "$PROJ/local.properties" ]; then
  cat > "$PROJ/local.properties" <<EOF
sdk.dir=$ANDROID_SDK
sdk.path=$SDK
takdev.plugin=$SDK/atak-gradle-takdev.jar
EOF
  echo "Wrote $PROJ/local.properties"
fi

# 5. Build. Stop any Gradle daemon that was started under the wrong JDK first.
cd "$PROJ"
chmod +x ./gradlew
./gradlew --stop >/dev/null 2>&1 || true
./gradlew assembleCivDebug

APK="$(find "$PROJ/app/build/outputs/apk/civ/debug" -name '*.apk' | head -1)"
[ -n "$APK" ] || die "Build finished but no APK was found."
mkdir -p "$REPO/dist"
cp "$APK" "$REPO/dist/"
echo
echo "Built: $REPO/dist/$(basename "$APK")"
