# Play Console checklist: closed test

Everything the app can supply is in this folder. The items marked **Owner** need the Play Console account.

## App content

| Item | Value |
|---|---|
| App name | `store/listing/<lang>/title.txt` (30 characters max, checked) |
| Short description | `store/listing/<lang>/short_description.txt` (80 max, checked) |
| Full description | `store/listing/<lang>/full_description.txt` (4000 max, checked) |
| Default language | English (United States); add Hebrew, Arabic, Russian as translations |
| Category | Productivity (alternative: Business) |
| Tags | Time tracking, Work, Payroll (pick what Play offers) |
| Contact email | info.hourstracker@gmail.com |
| App icon | `store/graphics/play-icon-512.png` (512 x 512) |
| Feature graphic | `store/graphics/feature-graphic-1024x500.png` (1024 x 500) |
| Phone screenshots | `store/graphics/screenshots/<lang>/` (1080 x 1920) |
| Release notes | `store/listing/<lang>/release_notes.txt` (500 max); wrap in `<en-US>`, `<iw-IL>`, `<ar>`, `<ru-RU>` tags when pasting |
| Privacy policy URL | **Owner:** needs a public URL. The repository is public, so once `store/legal/` is on `main`: `https://github.com/WoodbutcherTh1/HoursTracker-Android/blob/main/store/legal/PRIVACY_en.md` (pushing to `main` needs the owner's approval per `CLAUDE.md`) |
| Terms of use | `store/legal/TERMS_<lang>.md` (not required by Play; also shown in the app) |
| Data safety | `store/DATA_SAFETY.md` |

## Declarations

| Declaration | Answer |
|---|---|
| Contains ads | No |
| App access | All functionality available without login or special access |
| Target audience | 18 and over (a work tool; not designed for children) |
| Content rating questionnaire | No violence, sexual content, gambling, user-generated sharing or location; expect "Everyone" |
| Government app / news / health / financial features | Not a government app. Not a financial service: it estimates pay and tax for the user's own records and says so. Declare "no" to loan, banking and trading features |
| Advertising ID | Not used (no ads SDK). Answer "No" |
| Foreground service: `specialUse` | **Owner:** Play asks for a description and a short video. Description: "While the user has a shift running, a notification shows the running timer and live pay until they clock out. No other foreground service type fits a work timer." Video: clock in, pull down the notification shade, clock out |
| Permissions | `POST_NOTIFICATIONS` (shift timer, reminders, summary), `RECEIVE_BOOT_COMPLETED` (re-arm the optional shift-start reminder after a restart), `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` (above). No sensitive permissions |
| Exact alarms | Not used (break reminders use an inexact alarm) |

## Closed test (personal account)

12 testers who stay opted in for 14 days are required before applying for production. Upload `HoursTracker-1.0.0-alpha1.aab`, create a closed-testing track, add the testers' Google accounts or a Google Group, and share the opt-in link.

## Signing

- The bundle is signed with an **upload key**. Enrol in Play App Signing when asked (the default for new apps).
- Keystore and passwords are in `~/.hourstracker-signing/` on the build machine and mirrored into the untracked `local.properties`. **Back them up somewhere safe (a password manager).** Losing the upload key is recoverable through Play support; leaking it is not harmless, so never commit it.
- Rebuild: `./gradlew :app:bundleRelease` (output `app/build/outputs/bundle/release/app-release.aab`). Raise `versionCode` in `app/build.gradle.kts` for every upload.
- Upload-key SHA-256: `BF:9B:99:F8:94:A6:93:E4:98:BA:09:28:46:7E:C6:5C:E2:B3:23:F2:36:8A:DA:A0:13:B4:1A:62:DD:C3:75:C4`
