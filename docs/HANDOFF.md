# Handoff

State of the Android app at the end of Missions 6 to 9 (8 October 2026). Branch `feature/reorderable-stat-cards`; see `git log` for the commits (`feat(polish)`, `test`, `feat(release)`, `docs`).

## Where things stand

- **Builds and tests are green:** `:core-model:test`, `:app:testDebugUnitTest` (about 100 tests), `:app:verifyRoborazziDebug` (10 screens), `:app:lintDebug`, `:core-data:lintDebug`, `:app:bundleRelease`.
- **Signed bundle:** `~/Desktop/HoursTracker-Release/HoursTracker-1.0.0-alpha1.aab`, signed with the upload key in `~/.hourstracker-signing/` (passwords also in the untracked `local.properties`). **Back that folder up.**
- **Play Store material:** everything is in `store/` (listing in four languages, graphics, privacy policy, terms, data safety, checklist). What still needs the Play Console account is listed in `store/PLAY_CONSOLE_CHECKLIST.md`.
- **Coverage** of `app` is about 68% of instructions and 49% of branches (`createDebugUnitTestCoverageReport`).

## Checked on an emulator, and what was not

Smoke-tested the **debug build** on the Pixel_7a emulator (Android 17 preview, Hebrew system language): consent, onboarding, Home, the notification permission prompt, clock in (the foreground notification appeared with two actions), clock out; no crash in logcat; layout mirrored correctly.

**Not** checked on a device: the signed release bundle itself, the Android 12 splash and launcher icon on a real launcher, haptics, break reminders firing, the notification buttons, and Play's special-use foreground-service review. Do those before inviting testers (install the bundle with `bundletool`).

## Decisions made while working (change them if you disagree)

- **Roborazzi 1.76.0**, not 1.6.0 from the earlier notes (1.6.0 is old). Pictures are recorded on macOS; CI compares on Linux with Roborazzi's default 1% tolerance. If CI reports small anti-aliasing differences, record once on Linux and commit those.
- **Break reminder uses an inexact alarm** (`setAndAllowWhileIdle`), so no exact-alarm permission is needed.
- **Legal text rewritten for Android** (no iPhone, iCloud, Face ID, AI, camera or location sections). The Terms and Privacy `CURRENT_LEGAL_VERSION` stays 1, because nobody has accepted an Android version yet; raise it when the text changes after release.
- **`versionName` is `1.0.0-alpha1`**, `versionCode` 1.
- **R8 is off** for release (no device testing yet). Turn on `isMinifyEnabled` and `isShrinkResources`, then test every screen, Room and the notification.
- **Listing categories and declarations** are suggestions in the checklist, not submitted.
- **Theme:** the app follows the system; the picker and the break-reminder controls are not in Settings yet, so the listing says "follows your phone".
- **Bugs found by the new screenshots and fixed:** red delete background on every History row; raw `%1$s` in the Export language row; four stale unit tests; lint errors left by earlier missions.

## Still to do (from the earlier plan)

- 2.4 Edit sheet: times, breaks and notes
- 2.6 Bulk select and delete in History
- 2.7 Filter chips (Week, Month, Payroll, Year)
- 2.8 Search in History
- 4.1 Shift reminder before the shift starts
- 4.3 Shift summary notification
- 4.4 Unsaved-settings reminder
- 4.6 Per-reminder switches in Settings
- Settings UI: theme picker, break reminder controls
- Payslips tab (currently an empty state)
- Account, backup, restore from iOS (the importer is ready in `core-data`), FCM and server files (M4); widgets (M3)
- Closed test: 12 testers for 14 days, then production
- Network error state: `ErrorState` and the translated strings exist (`error_network_*`), unused until a screen goes online

## Next session, in this order

1. Run the release bundle on a phone and tick off the "not checked" list above.
2. Upload the bundle to a closed-testing track; fill the declarations (special-use foreground service needs a short video).
3. Settings: theme picker and break reminders (small, and the listing already works without them).
4. History: filter chips and search; edit sheet.
5. M4 (account and backup). Update `store/DATA_SAFETY.md` and the privacy policy **first**.

## Useful commands

```
./gradlew :app:recordRoborazziDebug                     # re-record the 10 screenshots after an intended design change
./gradlew :app:recordRoborazziDebug -PplayScreenshots   # also redraw the Play Store pictures (store/graphics/screenshots)
python3 store/tools/generate_feature_graphic.py         # then the feature graphic
python3 store/tools/generate_legal_docs.py              # after editing the in-app legal strings
python3 store/tools/generate_icons.py store/graphics    # after changing the icon design
./gradlew :app:bundleRelease                            # signed when local.properties has the RELEASE_* values
```
