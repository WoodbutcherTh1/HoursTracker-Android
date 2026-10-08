# Admin security requirements

**Status:** there is **no admin dashboard** in this repository or in the Android app today (it is planned as milestone M7). This document is the set of requirements it must meet before it is built or switched on. It is not a description of something that exists.

Scope: the owner's dashboard that will show aggregate numbers (installs, languages, app versions) from the server data described in `CLAUDE.md`. It must never show an individual's shifts, pay, name or ID number, because the server never receives them.

## Requirements

| # | Requirement | How |
|---|---|---|
| 1 | **MFA for every admin sign-in** | Supabase Auth TOTP multi-factor authentication; admin tables only readable when the session is `aal2` (the policy checks `auth.jwt() ->> 'aal' = 'aal2'`). A password alone must open nothing |
| 2 | **Read-only by default** | The dashboard uses a database role that has `SELECT` on aggregate views only. Any write (for example sending an announcement) goes through a named Edge Function, not through table access |
| 3 | **Aggregate only** | Views return counts, never rows of people. Minimum group size (for example 5) before a number is shown |
| 4 | **Every admin action is recorded** | Edge Functions write one line per action (who, what, when, from which function version, result) to an append-only `admin_audit` table that admins cannot update or delete. Reads of aggregates are logged at the function-log level |
| 5 | **Least privilege and separation** | A separate admin account per person, no shared logins. The `service_role` key exists only in Supabase function secrets and CI secrets, never in the dashboard front end, a repository or a mobile app |
| 6 | **Session hygiene** | Short sessions (for example 8 hours), re-authentication with MFA for destructive or bulk actions, sign-out on inactivity |
| 7 | **Network restrictions where possible** | Allow-list the admin origin in CORS (no wildcard); consider an IP allow-list or a VPN for the admin Edge Function |
| 8 | **Rate limits** | Per account and per IP on sign-in, MFA verification and every admin function |
| 9 | **Recovery without weakening** | Two recovery codes stored offline by the owner; no "disable MFA by email" path |
| 10 | **Review** | Quarterly: list admins, remove stale ones, read the admin audit table, test that a user without `aal2` is refused |

## Before launch checklist

- [ ] MFA enforced and tested with a password-only session (must be refused).
- [ ] `admin_audit` exists, is append-only, and has an entry for each action type.
- [ ] No policy uses `USING (true)` on an admin or user table.
- [ ] The dashboard bundle contains no `service_role` key (search the built files).
- [ ] CORS allows only the dashboard origin.
- [ ] A written owner-succession note exists (who can access if the owner cannot).
