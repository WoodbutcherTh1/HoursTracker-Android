# From iOS to Android

For people who use HoursTracker on iPhone and for anyone comparing the two apps. Status is for **1.0.0-alpha1**.

## Feature parity

| Area | iOS | Android 1.0.0-alpha1 |
|---|---|---|
| Clock in and out, live timer and live pay | Yes | **Yes** |
| Breaks (paid or unpaid) and a reminder before a break ends | Yes | **Yes** |
| Running-shift notification | Live Activity | **Foreground notification** with Break and Clock out buttons |
| Pay engine: daily and weekly overtime, rest days, holidays, sick days, night shifts | Yes | **Yes**, same numbers (proven bit for bit with golden files) |
| Tax estimate (income tax, National Insurance, health tax) | Yes | **Yes**, same brackets |
| History by payroll month, net and gross | Yes | **Yes** |
| Edit, delete (with undo) and add shifts by hand | Yes | **Yes** (swipe to delete); search, filter chips and bulk delete are not there yet |
| Export PDF and CSV | Yes | **Yes** |
| Hebrew, English, Arabic, Russian, right-to-left | Yes | **Yes** |
| Light and dark | Yes | **Yes**, follows the phone (no in-app switch yet) |
| Onboarding and legal consent | Yes | **Yes** |
| Reorder the Home stat cards | Yes | **Yes** (long-press and drag) |
| Account (email sign-in) | Yes | Planned (M4) |
| Cloud backup and restore | Yes (account, iCloud) | Planned (M4); opt-in |
| Payslips, timesheet scanner, assistant | Yes | Planned (M5); the tab shows an empty state |
| Lock the app | Face ID | Planned (M5) |
| Leave days, activity log, full data export, trend | Yes | Planned (M5) |
| Widgets, Quick Settings tile, shortcuts | Widgets, Watch | Planned (M3) |
| Location arrival reminders | Yes | Deferred |
| Watch app | Apple Watch | Planned (M6, Wear OS) |

## Moving your data

There is **no automatic transfer yet**. Your shifts live on the iPhone (and in your account backup, if you made one). What you can do today:

1. On the iPhone, export your shifts as CSV or PDF and keep the file. The Android app does not read these files back.
2. On Android, enter your settings (hourly rate, allowance, payroll month day) and add past shifts by hand with the **+** button in History if you need them.

What is coming (M4): restore from your iOS account backup. The reading code already exists in `core-data` (`BackupImporter`) and is tested; it is not connected to the screen yet. When it is, it will behave like this:

- **Days are placed on the same calendar date.** iOS stores a shift's day as midnight in the zone it was recorded in; the importer reads that day in Israel's time zone and puts it on the same date on the phone, so a shift of 15 July stays on 15 July.
- **Never overwrites newer data**: restore adds what is missing and keeps whatever is newer on the phone.
- **The ID number is not in the backup** and is not restored; enter it again in Settings.
- **Leave days and second jobs** are read, but this version has nowhere to keep them yet, and the screen will say how many were skipped.

## Differences you will notice

- **Cloud backup is opt-in and off by default**, and when it ships it will store only what you choose, never the ID number. iOS also offers iCloud sync; Android has no equivalent. Android's own device backup is turned off for this app on purpose, so a new phone starts empty until you restore.
- **Data on the phone stays on the phone.** Clearing the app's storage or uninstalling it deletes everything. Export before you do either.
- **Notifications:** Android 13 and later asks for permission the first time you clock in. If you say no, the shift is still recorded, but the timer will not show in the status bar; Home shows a banner with a shortcut to the setting.
- **Break reminders** are on by default and use an inexact alarm, so they can arrive a minute or two late. There is no switch for them in Settings yet.
- **Navigation:** the five tabs are in the same order. Use the system back gesture to leave Add or Edit shift.
- **Language:** change it inside the app (Settings) or let it follow the phone. Reports can use a different language from the app.
- **Fonts and number formats** follow Android's locale data, so a currency symbol can sit slightly differently from iOS.
- **Terms and privacy policy** are written for Android and Google Play (`store/legal/`); you accept them again on first launch.

## For developers

- Behavior is defined by the iOS app and proven by `golden/data/*.json` (see `golden/README.md`). If the two disagree, iOS wins; open an issue rather than "fixing" the Kotlin.
- Do not copy iOS source into this repository (it is public). Only generated data belongs here.
