# Changelog

All notable changes to HoursTracker for Android. Versions follow `versionName` in `app/build.gradle.kts`.

## [1.0.0-alpha1] - 2026-10-09 (second build)

Same version, rebuilt after the first emulator tests. Replace the first bundle.

### Added

- **History:** filter chips (Week, Month, Payroll, Year, All), search by date or amount (rows and totals follow it), long-press to select several shifts and delete them with confirmation and undo.
- **Settings > Appearance:** theme picker (Light, Dark, Auto). Status and navigation bar icons now follow the app theme.
- **Settings > Notifications:** separate switches for break reminders (with minutes), a shift start reminder (opt-in, minutes before the usual start time, skips rest days, survives restarts) and a shift summary notification after clocking out from the notification.
- **Settings:** asks to save or discard when you leave with unsaved changes. The language row shows a dropdown arrow, in the order Hebrew, English, Arabic, Russian, System.
- **Home:** the sparkline counts the running shift, so the last point grows while you work.
- **Release:** R8 minification and resource shrinking (bundle about 4 MB).

### Fixed

- **Arabic and Russian were missing from the build** (a locale filter still listed only English and Hebrew), so choosing them showed English. Both now ship.
- **PDF export crashed in every language** (seven text keys carried an iOS `%@` suffix and were never found).
- Editing a shift no longer drops its recorded breaks; a running shift cannot be opened for editing from History.
- The History edit and save toast ran on the wrong thread after a database call.

## [1.0.0-alpha1] - 2026-10-08

First build for the Google Play closed test.

### Added

- **Clock in and out** with a live timer and live estimated pay (net or gross), and a summary sheet when the shift ends.
- **Breaks** that stop the paid clock (or stay paid, per setting), and a reminder shortly before a break ends.
- **Running-shift notification** (foreground service) with Break and Clock out actions.
- **Home**: weekly sparkline; Today, Week and Month cards that can be reordered by long-press and drag.
- **History** by payroll month with net and gross totals; swipe to delete with undo; tap a shift to edit it; add a missed shift by hand.
- **Pay engine** ported literally from iOS and proven against iOS golden files: daily and weekly overtime (125% and 150%), rest days, holidays, sick days, night shifts, and Israeli income tax, National Insurance and health tax estimates.
- **Export** to PDF or CSV for a month, a year or a custom range, with day-type filter, report language and optional notes.
- **Settings** for worker details, workplace, pay and hours, work rules, payroll month, tax details and the app language.
- **Four languages** (Hebrew, English, Arabic, Russian) with full right-to-left layout.
- **Light and dark themes**, following the phone.
- **Onboarding** with language picker, and a legal consent screen with Android-specific terms and privacy policy.
- **App icon**, Android 12 splash screen, hand-drawn tab icons, skeleton loading, empty and error states, haptic feedback on clock in and out, screen transitions, and accessibility labels.
- **Tests**: golden tests for the pay engine, Robolectric UI tests, and Roborazzi screenshots of ten key screens (compared in CI).
- **Release tooling**: signed bundle build, Play Store listing text in four languages, graphics, privacy policy, terms and a Data Safety draft in `store/`.

### Fixed in this build

- History rows no longer show a red "Delete" background at rest.
- The Export language option no longer shows a raw `%1$s` placeholder.
- Break reminders use an inexact alarm, so no special alarm permission is needed.
- Notification texts are translated into all four languages.

### Known limitations compared with iOS

- No account, cloud backup or restore yet (and no import of iOS backups in the UI, although the importer exists in `core-data`).
- Payslips tab is an empty state: no payslip storage, timesheet scanner or assistant yet.
- No widgets, Quick Settings tile, shortcuts, location reminders, app lock or activity log.
- The edit screen is the full-screen Manual Entry form, not a bottom sheet.
- No Wear OS app.
