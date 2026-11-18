# Third-party notices

atak-garmin's own code is licensed under the [Apache License 2.0](LICENSE). It uses or ships the
following third-party material, each under its own licence.

| Component | Where | Licence |
|-----------|-------|---------|
| Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`), Copyright Gradle, Inc. and the original authors | `gradle/wrapper/`, repo root | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Libre Franklin fonts, Copyright (c) 2015 Impallari Type | `docs/user_manual/fonts/LibreFranklin-*.otf` | [SIL Open Font License 1.1](docs/user_manual/fonts/OFL.txt) |
| Latin Modern Sans fonts, Copyright 2003, 2008 B. Jackowski and J. M. Nowacki | `docs/user_manual/fonts/lmsans*.otf` | [GUST Font License](docs/user_manual/fonts/GUST-FONT-LICENSE.txt) |
| ATAK-CIV plugin template (build files, `PluginNativeLoader.java`, user manual template and images) | Starting point for this project | TAK Software License Agreement (comes with the ATAK-CIV SDK) |

## ATAK-CIV SDK

This plugin is built with the ATAK-CIV SDK from [tak.gov](https://tak.gov). The SDK itself (API jar,
build plugin, signing key, `atak.apk`) is **not** included in this repository and is covered by the
TAK Software License Agreement that ships with it. That agreement lets you build new works on the SDK
but not redistribute the SDK; get your own copy from tak.gov.

ATAK and TAK are products of the TAK Product Center. This project is not affiliated with or endorsed
by the TAK Product Center, the U.S. Government, or Garmin. Garmin and Foretrex are trademarks of
Garmin Ltd. or its subsidiaries.
