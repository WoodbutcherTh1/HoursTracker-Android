# Architecture

A 1:1 Android port of the iOS app HoursTracker, in behavior and design.

## Modules

```
core-model/   Pure Kotlin/JVM (java.time): pay engine, tax estimate, periods, live pay curve
core-data/    Room, repositories, importer for iOS backups, Supabase client      (M2+)
app/          Jetpack Compose, Glance widgets, services, shortcuts               (M2+)
wear/         Wear OS: Compose for Wear, Tile, Complication                      (M6)
golden/       Golden JSON files generated from iOS, and the Swift harness
docs/         This file, TAX_BRACKETS.md, proposed server files
```

- `core-model` has no Android dependency, so its tests run on any machine with a JDK.
- Dependency injection is manual (an `AppContainer`); no Hilt.
- minSdk 26 (`java.time` available), targetSdk 36.

## Decisions that define behavior

- **Week start and time zone follow the device** (`WeekFields.of(locale)`, `ZoneId.systemDefault()`), exactly as `Calendar.current` does on iOS. Tests pass both explicitly.
- **Overtime is per day only.** Weekly standard hours and the weekly overtime cap are stored and displayed, never used in pay.
- **Night shifts** change only the standard day (8.6 h to 7.0 h). There is no night premium.
- **The engine does not round.** Rounding happens when money is formatted.
- **Rest days** change the pay premium. The work-week pattern is display-only and is never bound to rest days.
- **Tabs** (same order as iOS): Home, History, Payslips, Export, Settings.

## The golden-test gate

The pay math is ported literally and proven against iOS:

1. A Swift harness (`golden/harness`) runs on a Mac against a local iOS checkout and writes JSON files.
2. Doubles are stored as raw bits; the Kotlin tests require bit-for-bit equality.
3. `golden/data/manifest.json` records the iOS commit SHA and generation date.
4. **No UI work starts until every golden test passes (milestone M1).**

No iOS source code is ever copied into this public repository, only the generated data.

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
