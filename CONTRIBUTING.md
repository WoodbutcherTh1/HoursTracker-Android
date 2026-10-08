# Contributing

The repository is public for review, and all rights are reserved (see `LICENSE`). Contributions are by invitation from the owner. These are the rules for anyone working in the code, including AI assistants; the hard rules are also in `CLAUDE.md`.

## Hard rules

- **No secrets in the repository.** Never commit `local.properties`, keystores (`*.jks`, `*.keystore`), `google-services.json`, `.env*` or API keys. CI runs a secret scan.
- **Never copy iOS source code here.** Only data derived from it (the golden JSON files) belongs in this repository.
- **Never touch the iOS repository or the production backend.** Server changes are proposed as files under `docs/server/`.
- **Pay math is a literal port.** Do not "improve" it. If the iOS behavior is unclear, stop and ask. Every change to `core-model` must keep the golden tests passing.
- **Privacy:** shifts, hours and pay never leave the device. Never send shift times, pay or ID numbers anywhere.
- **New third-party libraries need the owner's approval.** Approved: JUnit 5, kotlinx-serialization-json, the Jetpack set (Compose, Room, WorkManager, Glance), Supabase-kt, Firebase Messaging, and (test only) Robolectric and Roborazzi. No Hilt: dependency injection is manual.

## Code style

- Kotlin official style; four spaces; no wildcard imports in new code.
- **Warnings are errors** (`allWarningsAsErrors` for Kotlin; `warningsAsErrors` for lint). Fix the cause; do not suppress a warning without a comment saying why.
- Prefer a short comment that says *why* over one that repeats the code. Keep names consistent with the file you are in.
- UI uses the design tokens in `ui/theme` (`Palette`, `Space`, `Radius`, `DsText`), never raw colours or sizes.
- Every user-visible string is a resource, in **all four languages** (`values`, `values-iw`, `values-ar`, `values-ru`). Lint fails on a missing translation. Do not concatenate sentences; use format arguments and plurals.
- Layouts must work right to left. Check Hebrew or Arabic before you call a screen done.
- Add a `contentDescription` or merge semantics for anything that is not plain text; give clickable things a `Role`.

## Commits

- One commit per logical change. CI must be green on every commit.
- Message form: `type(scope): summary`, in the imperative, for example `feat(history): add swipe to delete` or `fix(export): show the phone language name`. Types: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`.
- The body says what and why, not how.

## Pull requests

1. Branch from `main` as `android/<topic>`. **Push only to `android/*` branches**; pushing to `main` needs the owner's explicit approval every time.
2. Keep the pull request to one topic. Say what changed, why, and how you checked it. Include screenshots for UI changes.
3. All CI jobs must pass: secret scan, core-model tests, app build and lint, screenshot comparison.
4. Never skip, disable or loosen a test to get green. If a test is wrong, fix it in its own commit and explain.

## Testing requirements

- **Pay engine:** golden tests (`./gradlew :core-model:test`).
- **Logic and screens:** a unit or Robolectric test for new behavior; for a bug fix, a test that fails without the fix.
- **Looks:** if a key screen changes on purpose, run `./gradlew :app:recordRoborazziDebug`, look at the changed pictures in `app/src/test/screenshots`, and commit them with the change. If a picture changes and you did not mean it to, that is a bug.
- Before you push:

  ```
  ./gradlew :core-model:test :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :core-data:lintDebug
  ```

- Tests must not depend on the real clock: pass a fixed `IosCalendar` (see `ScreenshotFixture`).

## Releasing

See `store/PLAY_CONSOLE_CHECKLIST.md`. Raise `versionCode` for every upload, update `CHANGELOG.md` and `store/listing/*/release_notes.txt`, and update `store/DATA_SAFETY.md` and the privacy policy **before** shipping any feature that sends data off the device.
