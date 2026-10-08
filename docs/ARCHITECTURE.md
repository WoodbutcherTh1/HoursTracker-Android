# Architecture

A 1:1 Android port of the iOS app HoursTracker, in behavior and design.

## Modules

```
core-model/   Pure Kotlin/JVM (java.time): pay engine, tax estimate, periods, live pay curve
core-data/    Android library: Room database, ShiftRepository, importer for iOS backups
app/          The Android app: Jetpack Compose UI, view models, settings, shift service, notifications
golden/       Golden JSON files generated from iOS, and the Swift harness
store/        Google Play listing, graphics, privacy policy and terms, and the scripts that make them
docs/         This file, TAX_BRACKETS.md, MIGRATION_FROM_IOS.md, HANDOFF.md, proposed server files
wear/         Wear OS (M6, not started)
```

- `core-model` has no Android dependency, so its tests run on any machine with a JDK.
- `core-data` depends on `core-model`; `app` depends on both. Nothing depends on `app`.
- minSdk 26 (`java.time` available), compileSdk and targetSdk 36. Built with JDK 17 (CI uses 21), Gradle 8.14, AGP 8.13, Kotlin 2.4 with `allWarningsAsErrors`.

## The app

### Layers

```
ui/        Composables and view models (one folder per screen: home, history, export, settings, manual, onboarding, legal, payslips)
domain/    App-level logic that is not UI: ShiftClock, HomeStats, HistoryCalculator, ExportBuilder, StatType
service/   ShiftController (clock in, break, clock out) and ShiftService (the running-shift notification)
data/      AppContainer, SettingsRepository, AppFlags, SecureIdStore, notification channels and break reminders
```

The pay rules never live in `app`. Screens call into `core-model` (`OvertimeCalculator`, `LivePayEngine`, `HistoryPeriodHelper`, `PayFormatter`) and show the result.

### Manual dependency injection (no Hilt)

`AppContainer` (`app/.../data/AppContainer.kt`) builds every long-lived object once: the Room database, `ShiftRepository`, `SettingsRepository`, `AppFlags`, and the `ShiftController`. `HoursTrackerApp` owns the container, and `MainActivity` hands it to Compose through the `LocalAppContainer` composition local. View models are created with `viewModelFactory { HomeViewModel(it) }`, which passes the container to the constructor.

Tests replace the container without any framework: the constructor takes a `calendarFactory` (a fixed clock) and `seedMockShifts`, and `HoursTrackerApp.createContainer()` is `open` so a test application can override it (`ScreenshotApp`).

### Compose UI

- **Single activity.** `MainActivity` shows `AppGate`: legal consent, then onboarding, then `AppRoot`.
- **Navigation.** `AppRoot` is a Navigation Compose `NavHost` with the five tabs (Home, History, Payslips, Export, Settings) and two pushed screens (manual entry, edit shift). `FloatingTabBar` is drawn over the content, so screens leave room at the bottom. Tabs cross-fade; pushed screens slide in.
- **Design system.** `ui/theme` holds the "Calm Neon" tokens (`Palette`, `Space`, `Radius`, `DsText`), light and dark colour sets chosen by `ThemeMode` (the app follows the system today), and `dsCard`. Screens use these tokens, never raw colours.
- **State.** View models expose `StateFlow`s built from the repositories; screens collect them with `collectAsState`. Screens that need to tell "still loading" from "empty" collect a nullable value (see History).
- **Shared states.** `ui/components/States.kt` has `SkeletonBox`, `EmptyState`, `ErrorState`, `NoticeBanner`; `PrimaryButton` has a `loading` mode.
- **Right to left.** Layout direction follows the language. Numbers are forced left to right inside Hebrew and Arabic text. The in-app language is applied in `MainActivity.attachBaseContext` and the activity is recreated when it changes.
- **Icons.** The tab icons are hand-drawn `ImageVector`s in `TabIcons.kt` (the Material set shipped with the app has no outlined equivalents). The launcher icon is a vector foreground plus PNGs made by `store/tools/generate_icons.py`.
- **Accessibility.** Controls have roles (`Role.Button`, `Role.Tab`, `Role.RadioButton`); icon-only controls have a spoken label; stat cards merge into one announcement; the clock buttons have a spoken action.

### Persistence

- **Room** (`core-data`): `AppDatabase` v1 with shifts, their breaks, workplace settings and leave days. Schemas are exported to `core-data/schemas`. The version is not released, so it can still change without a migration; after the first Play release every change needs one.
- **SharedPreferences**: `PrefsSettingsRepository` (pay settings and profile; values pass through `WorkplaceSettings`, which clamps them like iOS), `AppFlags` (consent, onboarding, language, gross or net).
- **ID number**: `SecureIdStore` encrypts it with an AES key held in the Android Keystore. It is never in Room or in the settings file.
- **Backup**: `allowBackup="false"`, and the extraction rules exclude everything, so nothing is copied to Google Drive or another device.

### The running shift

`ShiftController` is the one place that clocks in, starts and ends breaks and clocks out. Each action reads the stored state, applies the rule from `ShiftClock`, saves, and refreshes the notification. Both Home and the notification buttons call it.

`ShiftService` is a foreground service of type `specialUse` that keeps the ongoing notification visible while the app is in the background. The timer is the system chronometer, so it ticks without waking the app. The service stops when the shift is closed. `BreakReminderManager` schedules an inexact alarm shortly before a break ends (`BreakReminderReceiver` shows the notification).

### Release

Release builds are signed from `local.properties` or environment variables (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`); without them the bundle is unsigned. See `store/PLAY_CONSOLE_CHECKLIST.md`.

## Testing

| Kind | Where | Run |
|---|---|---|
| Golden tests (bit for bit against iOS) | `core-model` | `./gradlew :core-model:test` |
| Unit and Robolectric UI tests (JUnit 5 plus the Vintage engine for JUnit 4) | `app/src/test`, `core-data/src/test` | `./gradlew :app:testDebugUnitTest` |
| Screenshot tests (Roborazzi, ten key screens) | `app/src/test/.../screenshots`, pictures in `app/src/test/screenshots` | `./gradlew :app:verifyRoborazziDebug`; accept a design change with `:app:recordRoborazziDebug` |
| Play Store pictures | `PlayScreenshots.kt` | `./gradlew :app:recordRoborazziDebug -PplayScreenshots` |
| Coverage | | `./gradlew :app:createDebugUnitTestCoverageReport`, then open `app/build/reports/coverage/test/debug/index.html` |
| Lint (warnings are errors) | | `./gradlew :app:lintDebug :core-data:lintDebug` |

Screenshot tests run against a fixed clock (17 June 2026, 10:30 UTC) and fixed shifts, so a picture only changes when the screen does. The pictures are drawn on the JVM; a different operating system can differ by anti-aliasing, which Roborazzi tolerates up to 1% of pixels.

## Decisions that define behavior

- **Week start, minimal days in the first week, and time zone follow the device** (`WeekFields.of(locale)`, `ZoneId.systemDefault()`), exactly as `Calendar.current` does on iOS. Tests pass all three explicitly.
- **Overtime exists at two levels.** Per-session breakdowns price overtime per day. `aggregate`, which produces every period total, then re-prices hours above the weekly standard (default 42 h) at 125% and 150% (weekly cap default 12 h), grouped by the week of each session's clock-in. Row figures and totals can therefore differ when a week exceeds the weekly standard.
- **Night shifts** change only the standard day (8.6 h to 7.0 h). There is no night premium.
- **Rounding:** per-session breakdowns do not round; `aggregate` rounds its monetary totals to 2 decimals, half away from zero. Money is also rounded when formatted.
- **Rest days** change the pay premium. The work-week pattern is display-only and is never bound to rest days.
- **Tabs** (same order as iOS): Home, History, Payslips, Export, Settings.

## The golden-test gate

The pay math is ported literally and proven against iOS:

1. A Swift harness (`golden/harness`) runs on a Mac against a local iOS checkout and writes JSON files.
2. Doubles are stored as raw bits; the Kotlin tests require bit-for-bit equality.
3. `golden/data/manifest.json` records the iOS commit SHA and generation date.
4. **No UI work starts until every golden test passes (milestone M1).**

No iOS source code is ever copied into this public repository, only the generated data.

## iOS behaviors that need care when porting

Facts found in the iOS code; how to handle each is decided with the owner before the golden cases are written.

- `aggregate` takes a `calendar` parameter but groups days (and sick streaks) with `Calendar.current`; only the weekly grouping uses the parameter.
- The night window ends 8 **elapsed** hours after 22:00, not at 06:00 wall-clock, so it differs on daylight-saving days.
- A day's sessions share one consumed-hours counter, but each session uses its own standard day (8.6 h or 7.0 h) for its tier boundaries.
- The weekly excess is summed in Swift `Dictionary` order, which is randomized per process, so the last bits of that sum are not reproducible unless the inputs add exactly.
- Money formatting follows the device locale, and Apple's and Android's locale data can differ in symbol placement and bidi marks.
- Retirement age is computed from today's date, so golden cases must stay away from the 67th birthday.

## Milestones

| | Scope |
|---|---|
| M0 | Repository, Gradle, CI, docs, harness skeleton |
| M1 | `core-model` and golden tests (gate) |
| M2 | MVP app: clock in/out, breaks, live pay, History, Settings, CSV/PDF export, 4 languages with RTL, local notifications |
| closed test | Starts after M2 (personal account: 12 testers for 14 days) |
| M3 | Widgets (Glance), Quick Settings tile, Live Updates, shortcuts |
| M4 | Account (email OTP), backup, restore from iOS backup, FCM and server files |
| M5 | Payslips, timesheet scanner, assistant, app lock, leave days, activity log, full export, trend |
| M6 | Wear OS |
| M7 | Owner admin dashboard |

Deferred: location reminders, on-device OCR, Google Sign-In.

## Server changes

Server changes (a `platform` column, `ru` as a supported language, FCM sending) are proposed as files under `docs/server/` for the owner to review and apply. This repository never changes a production backend.

## Privacy

Shifts, hours, and pay stay on the device. The server receives only a random install id, push token, language, app version, watch/widget flags, and an on-shift flag. Cloud backup is opt-in and additive: it never overwrites newer local data. The ID number is kept in the Android Keystore, never in the database or backups.
