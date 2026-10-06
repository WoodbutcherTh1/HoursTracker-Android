# HoursTracker for Android

Android version of **HoursTracker** — a work-hours and pay tracker for hourly workers, built for Israeli labor rules (overtime tiers, rest-day and holiday premiums, estimated net pay). It is a 1:1 port of the iOS app in behavior and design.

> **All rights reserved. Source published for review only. No reuse without written permission.**

## Status

Early development. Nothing here is released yet.

## Principles

- **Your hours and pay stay on your device.** Cloud backup is opt-in.
- The server receives only a random install id, push token, language, app version, and watch/widget and on-shift flags. It never receives shift times, pay, or ID numbers.
- No secrets in this repository. Keys live in local, untracked configuration and in CI secrets.
