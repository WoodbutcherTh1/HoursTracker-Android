# HoursTracker for Android

Android version of **HoursTracker** — a work-hours and pay tracker for hourly workers, built for Israeli labor rules (overtime tiers, rest-day and holiday premiums, estimated net pay). It is a 1:1 port of the iOS app in behavior and design.

> **All rights reserved. Source published for review only. No reuse without written permission.**

## Status

Early development. Nothing here is released yet. Current milestone: **M0 (repository foundation)**, then **M1 (pay engine, proven against iOS with golden tests)**. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Build and test

Requires a JDK 17 or newer. No Android SDK is needed yet.

```
./gradlew :core-model:test
```

## Layout

- `core-model/` — pure Kotlin/JVM rules (pay engine and related logic)
- `golden/` — golden test data generated from iOS, and the Swift harness that produces it
- `docs/` — architecture, tax constants and the annual update checklist

## Principles

- **Your hours and pay stay on your device.** Cloud backup is opt-in.
- The server receives only a random install id, push token, language, app version, and watch/widget and on-shift flags. It never receives shift times, pay, or ID numbers.
- No secrets in this repository. Keys live in local, untracked configuration and in CI secrets.

## Security

To report a vulnerability, see [`SECURITY.md`](SECURITY.md).
