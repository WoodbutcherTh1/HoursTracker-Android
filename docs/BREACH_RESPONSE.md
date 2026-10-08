# Breach response plan (template)

**Not legal advice.** The deadlines and authorities below must be confirmed with counsel before the closed test. Today the app has no server and sends nothing off the phone, so the realistic incidents are: a leaked signing key, a malicious or faulty release, a leaked secret in the repository, or (after M4) a backend incident. Update this plan before M4 adds accounts and backup.

## 1. Detect

Sources: a vulnerability report to `info.hourstracker@gmail.com`; gitleaks failing in CI; Play Console pre-launch or vitals alerts; user reports; Supabase and Google security advisories (after M4); Dependabot alerts.

Anyone who suspects an incident writes to the owner immediately with: what, when, how they know, what data might be involved.

## 2. Assess (within 24 hours)

| Question | Answer decides |
|---|---|
| Was personal data accessed, lost, altered or disclosed? | Whether this is a *personal data breach* |
| Whose data, how many people, which kinds (shifts, pay, name, ID number, sick days)? | Severity |
| Was the data encrypted or unreadable to the party who got it? (ID number: Keystore-encrypted, never leaves the phone) | Risk to people |
| Is it ongoing? | Containment urgency |

Severity: **Low** (no personal data, or unreadable data), **Medium** (limited data, low risk of harm), **High** (sensitive data such as ID number or sick days, many people, or likely harm).

## 3. Contain (immediately)

- Leaked key or secret: rotate it (see `docs/SECRET_ROTATION.md`), revoke the old one, check logs for use.
- Bad release: halt the Play rollout, push a fixed build with a higher `versionCode`, and use staged rollout percentages.
- Backend (after M4): disable the affected Edge Function or policy, snapshot logs, do not delete evidence.
- Keep a timeline as you go (time, action, who).

## 4. Notify

| Who | When | Notes |
|---|---|---|
| Privacy authority | **Confirm with counsel.** GDPR Art. 33: within 72 hours of becoming aware, unless unlikely to risk people. For Israel, counsel must confirm the current duty to notify the Privacy Protection Authority of a severe security incident and the form it takes | Use template A |
| People affected | Without undue delay if the risk to them is high (GDPR Art. 34) | Use template B. Plain language, in the user's app language (he, en, ar, ru) |
| Google Play | If the incident affects the published app (for example a compromised signing key) | Via Play Console and Play support |
| Vendors | If a vendor caused or is affected | See `docs/VENDORS.md` |

### Template A: authority notification

> Controller: Hmam Kaadna (HoursTracker), info.hourstracker@gmail.com. Date and time of the breach and of discovery: ... Nature of the breach: ... Categories and approximate number of people and records: ... Likely consequences: ... Measures taken and proposed: ... Contact for more information: ...

### Template B: message to people

> **What happened:** In plain words. **What data:** exactly which. **What it means for you:** the real risk. **What we did:** containment. **What you can do:** concrete steps (for example, change a password, watch for scam messages). **Contact:** info.hourstracker@gmail.com. We are sorry.

## 5. Recover and learn (within 14 days)

Root cause, fix, a regression test, update of `docs/COMPLIANCE_AUDIT.md`, and a short written post-mortem (no blame). Record the incident in the table below.

## Contact tree

| Role | Person | Reach |
|---|---|---|
| Owner and incident lead | Hmam Kaadna | info.hourstracker@gmail.com |
| Legal counsel | *to be named before the closed test* | |
| Hosting / backend support (after M4) | Supabase support | via the Supabase dashboard |
| Google Play support | Play Console | |

## Incident log

| Date | Summary | Severity | Notified | Post-mortem |
|---|---|---|---|---|
| | none so far | | | |
