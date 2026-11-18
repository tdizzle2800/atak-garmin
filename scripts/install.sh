#!/bin/bash
# Install ATAK (the SDK build) and the Foretrex plugin over adb.
#
#   scripts/install.sh            # physical phone (ignores emulators)
#   scripts/install.sh emulator   # running emulator
#
# Optional environment variables:
#   ATAK_APK   path to atak.apk from the SDK  (default: next to the SDK folder)
#   ATAK_SDK   path to the unzipped SDK       (default: ~/Downloads/ATAK-CIV-5.8.0-SDK)
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-phone}"
SDK="${ATAK_SDK:-$HOME/Downloads/ATAK-CIV-5.8.0-SDK}"
ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
ADB="$ANDROID_SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"

die() { echo "ERROR: $*" >&2; exit 1; }

case "$TARGET" in phone|emulator) ;; *) die "usage: scripts/install.sh [phone|emulator]";; esac
[ -n "$ADB" ] && [ -x "$ADB" ] || die "adb not found. Install 'Android SDK Platform-Tools' in Android Studio's SDK Manager."

ATAK_APK="${ATAK_APK:-}"
if [ -z "$ATAK_APK" ]; then
  for c in "$(dirname "$SDK")/atak.apk" "$(dirname "$SDK")/sdk files/atak.apk" "$SDK/atak.apk"; do
    [ -f "$c" ] && { ATAK_APK="$c"; break; }
  done
fi
[ -n "$ATAK_APK" ] && [ -f "$ATAK_APK" ] || die "atak.apk not found. It comes with the ATAK SDK download; set ATAK_APK=/path/to/atak.apk"

PLUGIN_APK="$(find "$REPO/dist" -name '*.apk' 2>/dev/null | head -1)"
[ -n "$PLUGIN_APK" ] || die "No plugin APK in $REPO/dist. Run scripts/build.sh first."

"$ADB" start-server >/dev/null 2>&1
echo "adb sees:"
"$ADB" devices

if [ "$TARGET" = emulator ]; then
  SERIAL="$("$ADB" devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1; exit}')"
  [ -n "$SERIAL" ] || die "No running emulator. Start one (Android Studio > Device Manager) and wait for the home screen."
else
  SERIAL="$("$ADB" devices | awk '$2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"
  if [ -z "$SERIAL" ]; then
    if "$ADB" devices | grep -q unauthorized; then
      die "The phone is 'unauthorized'. Unlock it and tap Allow on the USB debugging prompt (tick Always allow), then re-run."
    fi
    die "No physical phone found. Turn on Developer options > USB debugging, use a data cable, set USB mode to File transfer, unlock the phone, and re-run."
  fi
fi
A=("$ADB" -s "$SERIAL")
echo "Target: $SERIAL ($("${A[@]}" shell getprop ro.product.model | tr -d '\r'))"

# The device CPU must be one atak.apk ships native libraries for.
DEV_ABIS="$("${A[@]}" shell getprop ro.product.cpu.abilist | tr -d '\r')"
APK_ABIS="$(unzip -l "$ATAK_APK" 2>/dev/null | grep -o 'lib/[^/]*/' | sort -u | sed 's#lib/##; s#/##' | tr '\n' ' ')"
echo "Device ABIs:   $DEV_ABIS"
echo "atak.apk ABIs: ${APK_ABIS:-none listed}"
if [ -n "$APK_ABIS" ]; then
  MATCH=0
  for a in $(echo "$DEV_ABIS" | tr ',' ' '); do
    case " $APK_ABIS " in *" $a "*) MATCH=1;; esac
  done
  [ "$MATCH" = 1 ] || die "atak.apk has no native libraries for this device's CPU. On an Apple-silicon Mac use an arm64-v8a emulator image."
fi

echo "Installing ATAK (large file, can take a few minutes)..."
if ! OUT="$("${A[@]}" install -r -g "$ATAK_APK" 2>&1)"; then
  echo "$OUT"
  case "$OUT" in
    *INSUFFICIENT_STORAGE*) echo "Not enough free storage on the device (emulator: raise Internal Storage in Device Manager).";;
    *UPDATE_INCOMPATIBLE*|*signatures*) echo "A differently signed ATAK (for example from the Play Store) is installed. Uninstall it first, then re-run.";;
    *USER_RESTRICTED*) echo "Developer options > turn on 'Install via USB', approve the prompt on the phone, then re-run.";;
  esac
  exit 1
fi
echo "$OUT"

echo "Installing Foretrex plugin..."
"${A[@]}" install -r -g "$PLUGIN_APK"

PKG="$("${A[@]}" shell pm list packages | tr -d '\r' | sed 's/package://' | grep -E '^com\.atakmap\.app' | head -1 || true)"
echo
echo "Installed. ATAK package: ${PKG:-unknown}"
if [ -n "$PKG" ]; then
  "${A[@]}" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  echo "Launched ATAK."
fi
echo "Accept ATAK's permission prompts and allow the plugin when it asks to load."
echo "Logs:  $ADB -s $SERIAL logcat | grep -i -E 'atak|foretrex|libaums'"
