# Compliance-ready baseline audit

**Status of this document:** engineering notes, not legal advice and not a certification. HoursTracker is **not** SOC 2, ISO 27001, HIPAA or GDPR certified or audited. The goal is that when a certification is pursued, most technical controls and evidence already exist. Nothing here may be used in marketing as a compliance claim.

Audit date: 9 October 2026. Scope: the Android app in this repository (build `1.0.0-alpha1`, second build). Reviewers (the four hats this audit is written under): **SOC 2** (security, availability, confidentiality), **ISO 27001** (risk and controls), **HIPAA** (health-data-adjacent practice; does not apply today), **GDPR** (EU privacy; also a useful yardstick for Israel's Protection of Privacy Law).

## 0. Read this first: what is different from the planning assumptions

The brief for this audit lists Supabase tables, FCM, Gemini OCR, location, login and an admin dashboard. **None of those exist in the Android app today.** The audit describes what the code does and marks the rest "planned, not implemented" so that nobody mistakes a plan for a control.

| Assumed | Reality in this repository (checked 9 Oct 2026) |
|---|---|
| Supabase (`user_backups`, `devices`, `profiles`) | No Supabase code or dependency. The manifest has **no `INTERNET` permission**, so the app cannot send anything off the phone. Planned for M4. |
| FCM push token | No Firebase dependency. Planned for M4. |
| Gemini payslip OCR | Not implemented (Payslips tab is an empty state). Planned for M5. |
| Location (optional) | No location permission, no location code. The database has two unused nullable columns for a future workplace location (`workplace_settings.locationLatitude/Longitude`), which nothing writes. |
| Login, backup, restore | None exist. Importer code for iOS backups exists in `core-data` but is not connected to any screen. |
| Admin dashboard, Edge Functions | Not in this repository (planned M7). Server work is delivered as proposed files under `docs/server/` (none yet). |
| Supabase RLS | Server side, outside this repository. Cannot be verified here. |
| 68% coverage | True for instructions (49% branches) on 8 Oct; measured by `createDebugUnitTestCoverageReport`. |

## A. Data inventory

### A.1 What the app stores, where, and how it is protected

| Data | Personal data? | Store | Protection | Leaves the phone? |
|---|---|---|---|---|
| Shifts: date, clock-in and out, break minutes and intervals, night flag, notes (free text), origin flags | Yes (working time; notes may hold anything the user types) | Room `work_session`, `break_interval` | App-private storage; Android encryption at rest where the device has it (see C) | Only if the user exports or shares a report |
| **Day type** including `sick` | **Yes, health-adjacent**: a sick day reveals a health-related absence | Room `work_session.dayType` | As above | As above (appears in exports) |
| Worker details: full name, employee number, workplace name, contractor name | Yes | SharedPreferences `settings` | App-private, excluded from backup | In PDF/CSV exports the user creates |
| Pay settings: hourly rate, allowance, overtime caps, currency | Yes (financial) | SharedPreferences `settings` | As above | In exports |
| Tax details: birth date, marital status, children, spouse employed | Yes (family status, age) | SharedPreferences `settings` | As above | Not exported |
| **National ID number** (optional) | Yes (high sensitivity) | SharedPreferences `secure_id`, **encrypted** with an AES-256-GCM key held in the Android Keystore | Key never leaves the Keystore; ciphertext excluded from backup | Only inside a PDF the user creates, if filled in |
| App choices: language, theme, reminder switches, consent version, onboarding answers | Low | SharedPreferences `flags`, `settings` | App-private | No |
| Exported reports (PDF, CSV) | Yes | App cache `exports/` | Cache; old files removed on the next export | Only through the Android share sheet, chosen by the user |
| Notifications (shift timer, reminders, summary) | Show hours and pay on the lock screen if the user allows | System notification shade | Standard Android visibility rules (`VISIBILITY_PRIVATE`) | No |

**Not collected:** advertising ID, device identifiers, contacts, photos, camera, microphone, location, analytics, crash reports, IP addresses.

### A.2 Retention (before this work)

Shifts, settings and profile stayed until the user deleted them, cleared the app storage or uninstalled. **No retention policy, no automatic deletion, no audit log, no in-app erasure.** Addressed by FIX 1 to 3.

### A.3 Access

| Who | Access |
|---|---|
| The user | Everything, through the app |
| The developer / owner | **No access.** No data leaves the phone, there is no server, no telemetry, no remote config. Support receives only what the user emails |
| Other apps | None (private storage, `allowBackup=false`, no exported content provider; the only provider is the share `FileProvider`, `exported=false`, per-grant read) |
| Admin dashboard (aggregate only) | Planned (M7). Not present. See `docs/ADMIN_SECURITY.md` for the requirements it must meet before it is built |

## B. Data flow

```
 User ──taps──▶ Compose UI ──▶ ViewModels ──▶ Repositories
                                                 │
        ┌────────────────────────────────────────┼────────────────────────────────┐
        ▼                                        ▼                                ▼
  Room database                         SharedPreferences                 Android Keystore
  (shifts, breaks, audit log)           (settings, profile, flags)        (AES key for the ID number)
  app-private; device encryption        app-private; device encryption    hardware-backed where available
        │                                        │
        └──────────────── cache/exports ◀────────┘     (PDF/CSV the user asked for)
                                  │
                                  ▼  user-initiated only
                         Android share sheet ──▶ app the user picks (email, Drive, ...)
```

| Hop | Today | Transport security |
|---|---|---|
| Device to Room, SharedPreferences, Keystore | Implemented | Local; Android file-based or full-disk encryption when the device provides it (all devices launched with Android 10+ must) |
| Device to share sheet / another app | Implemented, user-initiated | Handled by the receiving app; the file is a one-time `content://` grant |
| Device to Supabase (backup, profile, device row) | **Planned (M4), not implemented** | Will be HTTPS/TLS only; no `INTERNET` permission until then |
| Device to FCM (push token only) | **Planned (M4), not implemented** | HTTPS/TLS to Google |
| Device to Gemini (payslip text, user's own key) | **Planned (M5), not implemented** | HTTPS/TLS; needs its own consent and policy update first |
| Device to location services | **Not planned for v1** (deferred) | n/a |

## C. Existing controls (verified in the repository)

| Control | Evidence | Maps to |
|---|---|---|
| ID number encrypted with a Keystore AES-GCM key; never in the database or backups | `SecureIdStore`, `IdCipher`; `PersistenceTest` asserts the stored text is not the number | SOC 2 CC6.1, ISO A.8.24, GDPR Art. 32 |
| No network capability at all in this build | Manifest has no `INTERNET`; no networking libraries in `libs.versions.toml` | SOC 2 CC6.6, ISO A.8.20, GDPR Art. 25 |
| Backup and device transfer disabled | `allowBackup="false"`, `data_extraction_rules.xml`, `backup_rules.xml` exclude everything | GDPR Art. 25, ISO A.8.13 |
| No tracking, ads or analytics SDKs; no crash-reporting SDK | Dependency list; Data Safety draft; test added in FIX 6 | GDPR Art. 5(1)(c) |
| App-private storage, `FileProvider` not exported | Manifest | ISO A.8.3 |
| Only `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, foreground-service permissions | Manifest; test added in FIX 6 | Least privilege, ISO A.8.2 |
| Secret scanning on every push | `gitleaks` job in `.github/workflows/ci.yml` | SOC 2 CC6.1, ISO A.8.4 |
| Dependency update PRs | `.github/dependabot.yml` (GitHub Actions and Gradle, weekly) | SOC 2 CC7.1, ISO A.8.8 |
| Gradle wrapper checksum verified in CI; actions pinned to commit SHAs | `ci.yml` | SOC 2 CC8.1, ISO A.8.30 |
| Branch protection by rule: only `main` needs approval; CODEOWNERS | `CLAUDE.md`, `.github/CODEOWNERS` | SOC 2 CC8.1 |
| Warnings are errors; lint is clean | `allWarningsAsErrors`, lint `warningsAsErrors` | ISO A.8.28 |
| Visual regression tests of ten screens; about 185 tests; 68% instruction coverage | `./gradlew :app:verifyRoborazziDebug`, coverage report | SOC 2 CC8.1 |
| Release signing keys outside the repository | `.gitignore`, `local.properties`, `~/.hourstracker-signing/` | SOC 2 CC6.1, ISO A.8.24 |
| Privacy policy and terms that match the build; consent screen before use | `store/legal/`, `LegalConsentScreen`, `AppGate` | GDPR Art. 12 to 14 |
| Minimal, documented server data design for later | `CLAUDE.md` privacy rules | GDPR Art. 25 |

## D. Gaps (prioritised)

### P0 (done in this work: see section E)

| # | Gap | Fix |
|---|---|---|
| 1 | No audit log | FIX 1 |
| 2 | No data retention policy | FIX 2 |
| 3 | No in-app right to erasure | FIX 3 |
| 4 | Consent was only a version number: no timestamp, no record of acceptances | FIX 4 |
| 5 | No data export for the user (GDPR Art. 20) | FIX 5 |
| 6 | "Privacy by default" was true but not enforced by tests | FIX 6 |
| 7 | No admin security requirements (no admin exists yet) | FIX 7 (document) |
| 8 | No machine-readable security contact | FIX 8 |

### P1 (before the closed test; documents written in Part 3)

| Gap | Document |
|---|---|
| No breach notification plan | `docs/BREACH_RESPONSE.md` |
| No vendor / DPA registry | `docs/VENDORS.md` |
| No sub-processor list | `docs/SUBPROCESSORS.md` |
| No secret rotation policy | `docs/SECRET_ROTATION.md` |
| No dependency vulnerability scan beyond Dependabot version bumps | Open. Add GitHub Dependabot security alerts and OSV or `dependency-check` to CI (free). Not done here: it adds a CI job and needs the owner to enable repository settings |

### P2 (before production)

- SOC 2 / ISO 27001 readiness: this document, the control mapping in section F, and the policies above are the start. A real programme needs named owners, risk register reviews, access reviews and evidence collection over time.
- Penetration test: none. For a local-only app the surface is small (exported share files, notification content, intent handling). Repeat before M4 adds a network.
- Bug bounty: none. `SECURITY.md` and `docs/security.txt` provide private reporting.
- MFA for admin: no admin exists. Requirements are in `docs/ADMIN_SECURITY.md`.

### Residual risks to be aware of (not P0 but real)

1. **Notes are free text** and can contain anything. They are included in exports only if the user switches that on, and they are part of the personal data export and of erasure.
2. **Device-level encryption is out of our control.** On a rooted or unencrypted device the app-private files are readable. The ID number is the only value protected independently by the Keystore.
3. **Exported files leave our control** once shared. The cache copy is removed on the next export, not immediately.
4. **Notification text** (hours and pay) can show on a lock screen if the user allows it.
5. **Sick-day entries are health-adjacent.** If the app ever syncs to a server, treat them as special-category data (GDPR Art. 9) and re-assess HIPAA applicability with counsel.
6. **Legal basis and controller duties** (registration of a database with the Israeli Privacy Protection Authority above thresholds, EU representative if EU users are targeted) are legal questions this document does not decide. See the notes in `docs/BREACH_RESPONSE.md` and ask counsel before the closed test.

## E. Recommendations and what was done

| # | What | Why | How | Effort | Status |
|---|---|---|---|---|---|
| FIX 1 | Audit log | Accountability and evidence (SOC 2 CC7.2, ISO A.8.15, GDPR Art. 5(2)) | Room table `audit_log` (migration 1 to 2); entries for settings changes, shift create/update/delete, exports, consent, retention cleanup, erasure; hashes and field names only, never values; Settings > Activity log with filters and CSV export; purge after a configurable 365 days | 1 day | See checkpoint |
| FIX 2 | Retention policy | Storage limitation (GDPR Art. 5(1)(e), ISO A.8.10) | Setting "Auto-delete shifts older than" (never, 1, 2, 5, 10 years), default never, cleanup at start, logged | half day | See checkpoint |
| FIX 3 | Right to erasure | GDPR Art. 17 | Settings "Delete all my data", typed confirmation, wipes Room, preferences, the Keystore key, cache, alarms, notifications; writes one tombstone entry (no personal data) so there is proof it happened; restarts into onboarding. The Supabase wipe is **not applicable yet**: the hook is documented for M4 | 1 day | See checkpoint |
| FIX 4 | Consent versioning | Demonstrable consent (GDPR Art. 7(1)) | Version, accepted-at time and app version stored; every acceptance logged; re-consent when the version rises. **IP hash dropped on purpose**: the app has no server, and collecting an IP would be data we do not need (data minimisation) | half day | See checkpoint |
| FIX 5 | Data export | Portability and access (GDPR Art. 15, 20) | Settings "Download my data": versioned JSON with shifts, settings, profile, audit log, consents; never the ID number; shared through the share sheet | half day | See checkpoint |
| FIX 6 | Privacy by default | GDPR Art. 25 | Tests that fail if a network, location or camera permission, an analytics dependency, or an opt-out default appears | 2 hours | See checkpoint |
| FIX 7 | Admin MFA | SOC 2 CC6.1, ISO A.5.17 | `docs/ADMIN_SECURITY.md` (requirements for the future dashboard) | 2 hours | Document only |
| FIX 8 | security.txt | ISO A.5.5, coordinated disclosure | `docs/security.txt` (RFC 9116) plus a section in `SECURITY.md` | 1 hour | Document only |

## E.2 Privacy by default (FIX 6): each default, and what enforces it

| Default | State | Enforced by |
|---|---|---|
| Location | Off: no permission, no code. The two unused database columns for a future workplace location are never written | `PrivacyByDefaultTest` (permission list) |
| Cloud backup | Off and impossible: no `INTERNET`; Android backup and device transfer excluded | `PrivacyByDefaultTest` (manifest flag, extraction rules, permissions) |
| Analytics, advertising, crash reporting | None, no SDK, no crash handler | `PrivacyByDefaultTest` (dependency and source scan) |
| Network access | None | `PrivacyByDefaultTest` (no `INTERNET`, no networking library, no `HttpURLConnection`, `Socket`, `WebView`) |
| Shift start reminder | Off until the person turns it on | `PrivacyByDefaultTest` |
| Automatic deletion of shifts | Off (never); a choice that deletes data asks first | `PrivacyByDefaultTest`, `RetentionUiTest` |
| Notifications | The Android 13+ permission is the opt-in. Break reminders and the shift summary are on **but show nothing until that permission is granted**; the summary hides hours and pay on a locked screen (public version) | `PrivacyByDefaultTest` |
| ID number | Optional, empty until typed, Keystore-encrypted | `PersistenceTest`, `PrivacyByDefaultTest` |
| Share files | The only content provider is not exported and grants per file | `PrivacyByDefaultTest` |
| Consent | Required before any use; versioned and logged | `ConsentUiTest` |

If a future change needs one of these to differ (for example `INTERNET` for backup), the test fails on purpose: update section A and B, the privacy policy and `store/DATA_SAFETY.md`, then change the test in the same pull request.

## F. Control mapping (for a future audit)

This table says where evidence for each family would come from. "Gap" means no evidence yet.

| Framework area | Evidence today | Gap |
|---|---|---|
| SOC 2 CC1 to CC2 (governance, communication) | `CLAUDE.md`, `CONTRIBUTING.md`, `SECURITY.md` | Named security owner, annual policy review |
| SOC 2 CC3 (risk assessment) | Section D, residual risks | Risk register with owners and review dates |
| SOC 2 CC6 (logical access) | Keystore, private storage, no network, signing key handling | Admin access controls (when an admin exists) |
| SOC 2 CC7 (monitoring) | Local audit log, CI secret scan, Dependabot | No production monitoring (there is no server); incident drill |
| SOC 2 CC8 (change management) | One commit per change, CI required, pinned actions, CODEOWNERS | Required reviewers on `main` (GitHub setting) |
| SOC 2 A1 (availability) | Local-first: works offline | Backup and recovery tests (when backup exists) |
| SOC 2 C1 (confidentiality) | Encryption of the ID number, no telemetry, export controls | Data classification policy beyond section A |
| ISO 27001 Annex A.5 (organisational) | This audit, `docs/*` policies | ISMS scope statement, supplier reviews |
| ISO 27001 A.8 (technological) | Section C | Vulnerability scanning in CI |
| HIPAA | Not applicable: no covered entity, no PHI. Sick-day flag is treated as sensitive | Re-assess if health data, employers or clinics are ever involved; a BAA would then be needed with every processor |
| GDPR Art. 5, 25, 32 | Minimisation, defaults, encryption (above) | Records of processing (this audit is a draft of it) |
| GDPR Art. 12 to 22 (rights) | In-app export, erasure, correction (edit), consent record | Requests that need a human (email) have no SLA written yet: use 30 days |
| GDPR Art. 33 to 34 (breach) | `docs/BREACH_RESPONSE.md` (template) | Contact tree names, legal review |
| GDPR Art. 28 (processors) | `docs/VENDORS.md`, `docs/SUBPROCESSORS.md` | Signed DPAs, once vendors are really in use |

## G. How to keep this true

1. Any change that adds a permission, a network call, a dependency that talks to a server, or a new kind of stored personal data **must** update section A and B, `store/DATA_SAFETY.md` and the privacy policy in the same pull request. `PrivacyByDefaultTest` fails on the obvious cases.
2. Re-run the audit before each Play release and before M4 (backup, account, push) starts.
3. Review the residual risks every quarter.
