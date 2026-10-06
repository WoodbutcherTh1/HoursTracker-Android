# HoursTracker for Android — project rules

Android port of the iOS app HoursTracker (a separate repository). The goal is a 1:1 port in behavior and design, targeting Google Play.

## Hard rules

- **No secrets in the repo.** Never commit `local.properties`, `google-services.json`, keystores (`*.jks`, `*.keystore`), `.env*`, push keys, or API keys. Configuration comes from untracked local files and CI secrets.
- **Never copy iOS source code into this repo.** This repository is public. Only data derived from it (golden JSON files) belongs here.
- **Never touch the iOS repository or the production Supabase project.** Server changes are delivered as proposed files under `docs/server/` for the owner to review and apply.
- **Push only to `android/*` branches.** Pushing to `main` needs explicit approval from the owner each time.
- **One commit per logical change.** CI must be green. Never skip, disable, or weaken a test to get green.
- **Warnings are errors** (`allWarningsAsErrors`).
- **Third-party libraries need the owner's approval.** Approved so far: JUnit 5 and kotlinx-serialization-json (tests), plus the Jetpack set (Compose, Room, WorkManager, Glance), Supabase-kt, Firebase Messaging, and Roborazzi (dev-only) for later milestones. No Hilt: dependency injection is manual.

## Pay math (must match iOS exactly)

- The pay engine is a literal port. Do not "improve" it. If anything is ambiguous, stop and ask.
- **No UI work until every golden test passes** (milestone M1 gate). Golden files are generated from iOS and compared bit for bit.
- Overtime is **per day only**. `weeklyStandardHours` and `weeklyOvertimeCapHours` are stored and displayed but never affect pay.
- Night shifts change only the standard day (8.6 h to 7.0 h). There is no night premium rate.
- The engine does not round. Rounding happens only when formatting money.
- Rest days change the pay premium. **Never bind `WeekPattern` to rest days**; it is display-only.
- Week start and time zone follow the device (`WeekFields.of(locale)`, `ZoneId.systemDefault()`), as `Calendar.current` does on iOS. Tests pass both explicitly.

## Privacy

- Shifts, hours, and pay stay on the device. Cloud backup is opt-in and additive (never overwrites newer local data).
- The server receives only: random install id, push token, language, app version, watch/widget flags, on-shift flag.
- Never send shift times, pay, or ID numbers anywhere. The ID number lives in the Android Keystore, never in the database or backups.
