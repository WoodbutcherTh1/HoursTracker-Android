# Vendor and data processor registry

A vendor appears here the day it is added to the app, not later. **Today the Android app uses no vendor that receives user data**: it has no network access (no `INTERNET` permission). The rows marked *planned* describe what milestones M4 and M5 intend and must be reviewed again when they are built.

Links to terms change; open and read each before relying on it. This is a register, not legal advice.

| Vendor | Used for | Data it would process | Status | DPA / terms | Our obligations |
|---|---|---|---|---|---|
| **Google Play** (distribution) | Delivering the app; crash and vitals data that Android collects for developers who opt in (the app itself has no crash SDK) | Install and device statistics held by Google, not sent by the app | In use (after publication) | Google Play Developer Distribution Agreement | Accept the terms; answer the Data Safety form truthfully; keep `store/DATA_SAFETY.md` current |
| **Supabase** (Postgres, Auth, Edge Functions) | Account sign-in, optional cloud backup, device registration | Email address (sign-in), optional backup of shifts and settings (never the ID number), random install id, language, app version | *Planned (M4)* | https://supabase.com/legal/dpa | Sign the DPA; choose the region; row level security on every table; secrets only in Supabase secrets; list as sub-processor; breach duty (see `BREACH_RESPONSE.md`) |
| **Google Firebase Cloud Messaging** | Push notifications | Push token, language, app version | *Planned (M4)* | https://firebase.google.com/terms/data-processing-terms | Accept the data processing terms; never put shifts or pay in a message; delete tokens on account deletion |
| **Google Gemini API** | Reading text from a payslip or timesheet (optional) | The text the user chooses to send, with the user's own key | *Planned (M5)* | https://ai.google.dev/gemini-api/terms | Explicit consent each time before sending; no storage of the result on a server; policy and Data Safety update first |
| **Apple Push Notification service** | Push for the iOS app only | Device token | iOS only | Apple Developer Program License Agreement | Not part of the Android app |
| **GitHub** | Source hosting, CI, Dependabot, secret scanning | Source code and CI logs (no user data) | In use | GitHub terms and DPA | Keep secrets out of the repository; pinned actions |

## Rules for adding a vendor

1. Justify it (what can't be done locally).
2. Minimise what it receives; write it in the row above.
3. Review its terms and DPA; sign if required.
4. Update `docs/SUBPROCESSORS.md`, `docs/COMPLIANCE_AUDIT.md` (sections A and B), `store/DATA_SAFETY.md` and the privacy policy.
5. `PrivacyByDefaultTest` will fail until the new capability is listed deliberately.
