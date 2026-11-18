# atak-garmin

An ATAK plugin that sends routes and waypoints from the ATAK map to a Garmin Foretrex (tested with a Foretrex 601) over USB.
It adds a **Foretrex** button to the ATAK toolbar. The button turns what is on the map into a GPX file
and copies it onto the Foretrex through the phone's USB-OTG port.

It does three things:

- **Reads the map.** Visible user-dropped waypoints and all routes. Route control points and other marker types are left out.
- **Builds a GPX 1.1 file** and fits it to the Foretrex limits (table below). Anything it has to change
  (shortened names, thinned routes, dropped items) is listed in the dialog before you send.
- **Writes it to the Foretrex.** The Foretrex shows up as a small USB drive called `GARMIN`. Android mounts it
  but only lets apps reach it through its folder picker, so the first time you tap **Send**, pick the
  `GARMIN` drive and allow access. After that the plugin saves `ATAK_<date>_<time>.gpx` into `GARMIN/GPX/`
  directly. The Foretrex loads the file the next time it starts.

## Download

**[⬇ Download the latest APK (v0.4.0-beta)](https://github.com/tdizzle2800/atak-garmin/releases/download/v0.4.0-beta/atak-garmin-0.4.0-beta-civ.apk)** · [All releases](https://github.com/tdizzle2800/atak-garmin/releases)

- Needs **ATAK-CIV 5.8.0** (the regular Play Store version works). Release APKs are signed by TAK.gov.
- Install the APK, then in ATAK open **Plugins** and load **ATAK Garmin**. A **Foretrex** button appears in the tools menu.
- Upgrading from 0.1.0-beta.1? Uninstall it first (it was signed with a different key). Later versions install over each other.

## Status

- **Builds.** `assembleCivDebug` succeeds against the ATAK-CIV **5.8.0** SDK.
- **GPX code is tested** on a desktop JVM (6 checks, see "GPX self test").
- **Works on real hardware.** A full ATAK-to-Foretrex transfer is confirmed on a Foretrex 601 with the
  TAK.gov-signed release on ATAK-CIV 5.8.0: waypoints sent from ATAK show up on the device.
- **Not tested yet:** other Foretrex models (401, 701). They use the same `GARMIN/GPX` folder, so they
  should work the same way.

## What you need

| Thing | Notes |
|-------|-------|
| ATAK-CIV 5.8.0 SDK | **Not in this repo.** Download it from tak.gov and unzip it. It comes with `atak.apk`, the build plugin and the API jar. |
| Android SDK | Android Studio installs it. Gradle fetches Platform 36 by itself on the first build. |
| JDK 17 or 21 | **Not 25**: Gradle 8.14 fails with "Unsupported class file major version 69". |
| Internet on the first build | The Gradle wrapper downloads Gradle 8.14.3 and the Android build tools. |
| Phone with USB host (OTG) | Plus a USB-OTG adapter or cable that carries data. |

A debug build is signed with the SDK key, so it only loads in the **SDK build of ATAK** (`atak.apk` from
the SDK, same version), not the Play Store ATAK. To run on the Play Store ATAK, use a release APK from
GitHub Releases: those are built and signed by TAK.gov's Third Party Pipeline (see "Signed release builds").

## Build

Unzip the SDK so it sits at `~/Downloads/ATAK-CIV-5.8.0-SDK`, or point `ATAK_SDK` at it, then:

    git clone https://github.com/tdizzle2800/atak-garmin.git && cd atak-garmin
    scripts/build.sh
    # or: ATAK_SDK=/path/to/ATAK-CIV-5.8.0-SDK scripts/build.sh

The script picks a JDK 17/21, copies the project to `<SDK>/samples/atak-garmin` (the SDK's build plugin
needs it there), writes `local.properties` (paths only, no passwords) and runs `./gradlew assembleCivDebug`.
The APK ends up in `dist/ATAK-Plugin-atak-garmin-0.3.0--5.8.0-civ-debug.apk`.

If `main.jar` is not inside the SDK folder, the script looks for `../sdk files/main.jar` or `ATAK_MAIN_JAR`.

## Signed release builds

Release APKs come from TAK.gov's [Third Party Pipeline](https://tak.gov/user_builds): zip the repo with a
single `atak-garmin/` root folder, upload it, and download the signed APK when the build succeeds. The
pipeline runs `assembleCivRelease` and fetches the ATAK SDK that matches `ATAK_VERSION` itself.
`ATAK_VERSION` must match the ATAK on the phone (the Play Store ATAK-CIV 5.8.0 refuses plugins built for 5.8.0).

## Install

Phone with USB debugging on, plugged into the Mac (use `emulator` for a running emulator):

    scripts/install.sh              # physical phone, ignores emulators
    scripts/install.sh emulator

It checks the device CPU against `atak.apk`, installs ATAK, then the plugin, and starts ATAK.
`atak.apk` is looked for next to the SDK folder; override with `ATAK_APK=/path/to/atak.apk`.
In ATAK, accept the permission prompts and allow the plugin when it asks to load.

## Use it

1. Drop some waypoints and a route in ATAK.
2. Plug the Foretrex into the phone (OTG), switch it on, and **plug in only the Foretrex**.
3. Tap **Foretrex** in the toolbar. Read the summary. Tap **Send**.
4. Accept Android's USB permission prompt the first time.
5. When the success message appears, unplug, and check the Foretrex's waypoint and route lists.

## Check the Foretrex first (5 minutes)

Before blaming the plugin, make sure the Foretrex takes a GPX file from `Garmin/GPX/`:

1. Plug the Foretrex into a computer. It appears as a drive.
2. Send one waypoint and one route to it with Garmin's own software, and note what appears in `Garmin/GPX/`.
3. Delete those, copy `sample.gpx` from this repo into `Garmin/GPX/`, eject, and look at the Foretrex lists.
   You should see `TEST1`, `TEST2` and a route `TESTRTE`.

If it does not, the device wants a different folder or format. Change `DEVICE_GPX_PATH` in
`ForetrexLimits.java`, or the file name in `PluginTemplate.send()`.

## Foretrex limits used

All in `app/src/main/java/com/atakgarmin/core/ForetrexLimits.java`.

| Limit | Value | Source |
|-------|-------|--------|
| Waypoints | 500 | published specs |
| Routes | 20 | published specs |
| Name length | 10 characters, upper case, ASCII only | **assumed**, verify on the device |
| Points per route | 250 (longer routes are thinned evenly, first and last kept) | **assumed**, verify on the device |

Waypoints and routes with invalid coordinates are skipped. A route needs at least 2 points.

## GPX self test

The GPX classes are plain Java, no Android needed:

    mkdir -p /tmp/atak-garmin-classes
    javac -d /tmp/atak-garmin-classes $(find app/src/main/java/com/atakgarmin/core app/src/test/java/com/atakgarmin/core -name '*.java')
    java -cp /tmp/atak-garmin-classes com.atakgarmin.core.GpxWriterSelfTest      # ALL PASSED
    java -cp /tmp/atak-garmin-classes com.atakgarmin.core.MakeSampleGpx sample.gpx

## Files

    app/src/main/java/com/atakgarmin/core/       GPX model, writer and device limits (plain Java)
    app/src/main/java/.../plugintemplate/plugin/
        PluginTemplate.java                      toolbar button, summary dialog, send
        AtakMapReader.java                       reads waypoints and routes from the ATAK map
        GarminDriveExporter.java                 Folder picker (first time) and write to GARMIN/GPX/
    app/src/test/java/com/atakgarmin/core/       self test and sample.gpx generator
    scripts/build.sh, scripts/install.sh         build and install helpers
    sample.gpx                                   two waypoints and a route for the manual check

The Gradle files, wrapper, `docs/` and `PluginNativeLoader.java` are the standard plugin project layout
from the ATAK SDK's plugin template. The Java package name `com.atakmap.android.plugintemplate.plugin`
is kept from that template.

## Logs

    adb logcat | grep -i -E 'atak|foretrex|GarminDrive'

## Not done yet

- Confirming the Foretrex import on real hardware (see the manual check above).
- The USB code writes to the **first** USB drive it finds, so connect only the Foretrex.
- Sending only selected items (right now it sends everything visible on the map).

## Not in this repo

The ATAK SDK (API jar, build plugin, signing key, `atak.apk`) is not included. Get it from tak.gov and
follow its terms. `.gitignore` keeps `*.apk`, `main.jar`, `local.properties` and build output out of git.

## License

This project's own code is licensed under the [Apache License 2.0](LICENSE). Third-party parts (the Gradle
wrapper, the user manual fonts and the ATAK plugin template) keep their own licences; see
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
