# Handoff

State of the Android app on 9 October 2026, after compliance hardening. Branch `feature/reorderable-stat-cards`, all commits pushed. `git log` has one commit per task.

## Where things stand

- **Green:** `:core-model:test`, `:app:testDebugUnitTest` (about 185 tests), `:app:verifyRoborazziDebug` (10 screens), `:app:lintDebug`, `:core-data:lintDebug`, `:app:bundleRelease` (R8 on).
- **Signed bundle:** Will be at `~/Desktop/HoursTracker-Release/HoursTracker-1.0.0-alpha1.aab` after tonight's build completes. Upload key in `~/.hourstracker-signing/` (also in the untracked `local.properties`). Back it up.
- **Play Store material:** `store/` (listings in four languages updated for the new features, 20 screenshots, feature graphic, privacy policy, terms, data safety, checklist). Still needs the Play Console account: see `store/PLAY_CONSOLE_CHECKLIST.md`.

## Latest work: Compliance hardening

All GDPR/CCPA controls now implemented and tested:

| Feature | Status |
|---|---|
| Consent record system | ✅ Version-tracked, logged acceptances, re-consent on policy version change |
| Activity log | ✅ Full audit trail with filters and CSV export, configurable purge |
| Download my data | ✅ Versioned JSON export (never includes ID number) |
| Delete all my data | ✅ Typed confirmation, full wipe including Android Keystore, tombstone record, app restart |
| Automatic deletion | ✅ Optional old shift deletion (never by default), confirmed and logged |
| Privacy-by-default tests | ✅ Lock-screen-safe notifications enforced by test |
| Security documentation | ✅ security.txt, breach response plan, vendor register, secret rotation policy |

Privacy policy and terms updated to disclose all controls, retention periods, and data flows.

## Previous work (overnight queue)

| # | Task | Result |
|---|---|---|
| 1 | Settings save + unsaved dialog | Done. Nothing was auto-saved; the draft survived tab switches and looked saved. Leaving Settings with edits now asks Save / Discard |
| 2 | Language picker | Done: chevron, order he/en/ar/ru/system. **Real bug found:** Arabic and Russian were filtered out of the build |
| 3 | Sparkline | Done: counts the running shift, no clipping, spoken description |
| 4 | CLAUDE.md push rule | Done: `feature/*` and `android/*` allowed; only `main` protected |
| 5 | History edit | Done as the existing full-screen form; keeps recorded breaks; running shift not editable from History |
| 6 | Bulk select | Done: long-press, tap, confirmation, undo |
| 7 | Filter chips | Done: Week, Month, Payroll, Year, All |
| 8 | Search | Done: date spellings, month and weekday names, amounts; totals follow |
| 9 | Reminders (4.1, 4.3, 4.4, 4.6) | Done: shift start reminder (opt-in), summary notification, switches. 4.4 is the Task 1 dialog |
| 10 | Theme picker | Done: Light / Dark / Auto, bar icon contrast follows the app theme |
| 11 | Break reminder controls | Done inside Task 9 (switch + minutes) |
| 12 | R8 | Done and working on the emulator. **Real bug found:** every PDF export crashed (copy keys with an iOS `%@` suffix) |
| 13 | Paywall | Skipped (out of scope) |
| 14 | Docs | Done |

## Checked on the emulator (Pixel_7a, Hebrew system language, API 37)

Release build with R8: consent, onboarding, Home, notification permission prompt, clock in, break, clock out and the summary sheet, History, the PDF export share sheet. Russian UI. Dark theme. Settings sections including all new Privacy & Data controls. Activity log. Data export. Data deletion flow. Not checked on a real phone, and not checked at all: the shift start reminder firing at its time, the boot receiver, the notification buttons, haptics, the splash on a real launcher, and the Play special-use foreground-service review.

## Decisions made while working (change them if you disagree)

- **Edit stays a full-screen form**, shared with Manual Entry, rather than a bottom sheet (same fields and validation; a sheet would duplicate a working screen).
- **Reminder switches apply immediately** and are not part of Save, like language and theme.
- **Shift start reminder is off by default** (opt-in), inexact alarm, 5 to 120 minutes before the usual start time, skips rest days and any day you are already clocked in. Added the `RECEIVE_BOOT_COMPLETED` permission to re-arm after a restart (listed in the Play checklist).
- **Summary notification only for shifts closed from the notification**; Home shows its own sheet.
- **Search** matches date spellings (including month and weekday names) and net or gross amounts; a letter such as "e" therefore matches month names. Totals follow the search.
- **Bulk delete skips the running shift.**
- **Robolectric cannot run `PdfDocument`**, so there is no PDF unit test; the key lookups are covered by `ExportCopyTest` and the real PDF was checked on the emulator. A device or instrumented test would close that gap.
- **R8 rules** are minimal (`app/proguard-rules.pro`); Room, kotlinx-serialization and Compose ship consumer rules. Watch the first crash reports for missing-class errors.
- **Consent re-prompting:** Policy version changes trigger re-consent on next launch (version stored in Room, checked before navigation).
- **Activity log retention:** Defaults to 90 days, configurable in Settings, enforced on app launch.
- **Data deletion is irreversible:** ID number destroyed from Android Keystore; tombstone record prevents confusion if user restores old backup.
- **Automatic deletion is opt-in only:** Never enabled by default; requires explicit user selection of retention period.

## Still to do

- Test the signed bundle on a real phone (install with `bundletool`), especially reminders, notifications, and the new privacy controls.
- Upload to a closed-testing track; fill the declarations (special-use foreground service needs a short video).
- The "Contact support" entry the legal text used to mention does not exist; the policy now says to email.
- Payslips tab is still an empty state. Account, backup, restore from iOS (the importer is ready in `core-data`), FCM and server files (M4); widgets (M3).
- Network error state: `ErrorState` and the translated strings exist, unused until a screen goes online.
- Closed test: 12 testers for 14 days, then production.
- Compliance review: Have legal review the privacy policy, terms, consent flow, and data handling documentation.

## Useful commands

```
./gradlew :app:recordRoborazziDebug                     # re-record the 10 screenshots after an intended design change
./gradlew :app:recordRoborazziDebug -PplayScreenshots   # also redraw the Play Store pictures (store/graphics/screenshots)
python3 store/tools/generate_feature_graphic.py         # then the feature graphic
python3 store/tools/generate_legal_docs.py              # after editing the in-app legal strings
./gradlew :app:bundleRelease                            # signed when local.properties has the RELEASE_* values
./gradlew :app:assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk   # same code as the bundle, easy to try
```
