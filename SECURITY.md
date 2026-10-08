# Security Policy

## Reporting a vulnerability

Please report security issues privately by email: **info.hourstracker@gmail.com**

Do not open a public issue for a vulnerability. Please do not include personal data (real shifts, pay, ID numbers) in your report.

Helpful details: what you found, how to reproduce it, the app version, and the Android version and device.

## What to expect

- **Acknowledgement:** within 3 business days.
- **Initial assessment:** within 14 days.
- **Fix:** timing depends on severity. You will be kept informed.
- **Disclosure:** please allow us to ship a fix before you publish details.

## Scope

This repository is the Android app, its tests, and supporting documentation. Secrets are never stored in it. If you find a committed secret, report it the same way.

## Machine-readable contact

`docs/security.txt` follows RFC 9116 (contact, expiry, languages, policy link). This project has no website to serve it from today; if one is added, publish the file at `/.well-known/security.txt` and renew its `Expires` date at least once a year (the current one is 1 October 2027).

## What to expect

We acknowledge a report within 5 working days, tell you what we found, and credit you if you wish. Please do not publish details before a fix is available. The app has no server today and cannot send your data anywhere (see `docs/COMPLIANCE_AUDIT.md`), so the most useful reports concern the exported files, notifications and local storage.

