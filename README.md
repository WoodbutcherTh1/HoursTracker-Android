# HoursTracker for Android

Android version of **HoursTracker**, a work-hours and pay tracker for hourly workers, built for Israeli labor rules (overtime tiers, rest-day and holiday premiums, estimated net pay). It is a 1:1 port of the iOS app in behavior and design.

> **All rights reserved. Source published for review only. No reuse without written permission.**

## Status

Version **1.0.0-alpha1**, ready for the Google Play closed test. Clocking in and out with live pay, breaks, History (filters, search, multi-select), manual entry, CSV and PDF export, optional reminders, a theme picker, and Hebrew, English, Arabic and Russian all work. Widgets, account and cloud backup, payslips, the scanner and Wear OS are still to come; see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (Milestones) and [`CHANGELOG.md`](CHANGELOG.md).

## Build requirements

- **JDK 17 or newer** (CI uses 21)
- **Android SDK** with platform 36 (`compileSdk = 36`, `minSdk = 26`) and build-tools 36. Android Studio installs both.
- A `local.properties` file in the repository root with `sdk.dir=/path/to/Android/sdk` (Android Studio writes it; it is never committed)
- Internet access the first time, to download Gradle and the dependencies

## Build, test, run

```
./gradlew :core-model:test                 # pay engine and golden tests (JDK only)
./gradlew :app:testDebugUnitTest           # unit, Robolectric UI and screenshot-guard tests
./gradlew :app:lintDebug :core-data:lintDebug
./gradlew :app:assembleDebug               # app/build/outputs/apk/debug/app-debug.apk
```

### Run on an emulator

1. In Android Studio, create a device (for example a Pixel 7a with API 35) and start it. Or from the command line: `$ANDROID_HOME/emulator/emulator -avd <name>`.
2. `./gradlew :app:installDebug`
3. `adb shell am start -n com.hourstracker.app/.MainActivity`

On first launch you accept the terms and privacy policy, then pick a language and answer a few questions. Android 13 and later ask for notification permission when you first clock in; the shift is recorded either way.

### Debug build extras

Debug builds fill History with about three months of mock shifts the first time they run. In **Settings**, scroll to the bottom for the **Debug** section; **Load overtime week (54h)** replaces last week with six 9-hour days so the weekly overtime rule can be checked by eye. Release builds have neither.

### Switching language

The app follows the phone's language by default. To change it inside the app, open **Settings** and use the language row (Hebrew, English, Arabic, Russian, or System); the screen restarts and mirrors for right-to-left languages. The row is a dropdown (Hebrew, English, Arabic, Russian, System). The same picker is on the first onboarding screen.

### Screenshots and coverage

```
./gradlew :app:verifyRoborazziDebug                    # compare the ten key screens with app/src/test/screenshots
./gradlew :app:recordRoborazziDebug                    # accept a design change (then commit the new pictures)
./gradlew :app:createDebugUnitTestCoverageReport       # HTML report in app/build/reports/coverage/test/debug
```

### Release bundle

`./gradlew :app:bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`. It is signed when the four `RELEASE_*` values are in `local.properties` (or the environment); see [`store/PLAY_CONSOLE_CHECKLIST.md`](store/PLAY_CONSOLE_CHECKLIST.md).

## Layout

- `core-model/`: pure Kotlin/JVM rules (pay engine and related logic)
- `core-data/`: Room database, repositories, iOS backup importer
- `app/`: the Android app (Compose)
- `golden/`: golden test data generated from iOS, and the Swift harness that produces it
- `store/`: Play Store listing text (four languages), graphics, privacy policy, terms, data-safety draft
- `docs/`: architecture, tax constants and the annual update checklist, migration guide, handoff

## Principles

- **Your hours and pay stay on your device.** Cloud backup, when it ships, is opt-in.
- The server (when features that need it ship) receives only a random install id, push token, language, app version, and watch/widget and on-shift flags. It never receives shift times, pay, or ID numbers.
- No secrets in this repository. Keys live in local, untracked configuration and in CI secrets.

## More

- [`CONTRIBUTING.md`](CONTRIBUTING.md): code style, commits, pull requests, tests
- [`docs/MIGRATION_FROM_IOS.md`](docs/MIGRATION_FROM_IOS.md): what is the same and what differs from the iOS app
- [`SECURITY.md`](SECURITY.md): reporting a vulnerability
