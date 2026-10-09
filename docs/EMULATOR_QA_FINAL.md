# Emulator QA — Final

**Date:** 2026-10-09 | **Device:** emulator-5554 (Pixel 7a, 1080x2400) | **Build:** `app-debug.apk` (fresh install, package `com.hourstracker.app`), Hebrew/RTL locale
**Method:** one screenshot per step, via `adb` taps. No app crashes or ANRs in logcat. The only native crash is in the emulator's UWB system service and is unrelated to the app.

## Result summary

| Screen | Loads | Verdict |
|--------|-------|---------|
| Onboarding (consent, rate, week, hours, preview) | Yes | Works; 2 numbers to verify |
| Home | Yes | Works; 1 formatting issue |
| History | Yes | Works; 3 issues |
| Settings | Yes | Works; 1 issue |
| Export (PDF + share sheet) | Yes | Works; 3 issues |
| Payslips | Yes | Empty state OK; no add control found |

## Findings

Severity: **High** = likely wrong behavior, **Med** = visible defect, **Low** = polish.

1. **High — pay period range is reversed.** History, Settings and Export all show the current period as `31.10 – 01.10` (Export: `10/31 – 10/01`). With pay-period start day 1 it should be 01.10–31.10. The same wrong value on three screens points to the period computation, not a display bug.
2. **Med — Export date format is inconsistent.** Export shows month-first `10/31`, while History and Settings use day-first `31.10`.
3. **Med — footer clipped behind the nav bar.**
   - History: the totals row ("hours" / "total") sits partly under the floating nav bar.
   - Export: the preview card (days / hours / gross / net) is cut off by the Export button.
4. **Low — time format inconsistent in History.** Entry and exit times are not zero-padded (`8:00`, `6:45`) while durations are (`08:15`).
5. **Low — hours format inconsistent.** Export preview shows decimal `56.3`; History shows `56:15`.
6. **Low — Home month card shows a bare `7`** (no unit or HH:MM), while the Today and Week cards show `00:00` and `45:30`.
7. **Low — Payslips has no visible add control.** The empty state says payslips "you add" will be stored on the device, but there is no add button on the screen.
8. **Verify (not asserted as bugs) — onboarding numbers.** The pay engine is a literal iOS port, so these may be correct premium rules, but I did not confirm them against the golden files:
   - 42 h/week at ₪50 shows ≈ ₪2,275.00 (flat rate would be 2,100).
   - The "8 hours today" sample at ₪50 shows ₪435.00 (flat rate would be 400).

## Verified OK

- Consent gate: Continue is disabled until the checkbox is ticked.
- Onboarding rate (₪50) carries into Settings.
- Settings defaults match the project rules: standard day 8.6 h, daily overtime cap 2 h, weekly standard 42 h, weekly cap 12 h. Rest day is Saturday, currency ILS.
- History totals: hours match the rows (56:15). Net total ₪2,747.95 vs sum of displayed rows ₪2,747.94 (1 agora, expected from per-row display vs rounded aggregate).
- Export preview net (₪2,747.95) matches History.
- PDF export builds `HoursTracker_2026-10-09_1357.pdf` and opens the system share sheet; Back returns cleanly.
- Bottom navigation and RTL ordering work on all five tabs.

## Not tested

Clock-in/out and break flows, adding or editing a shift, other export formats (not PDF), the report-language and notes options, Settings save, and the ID number field. Home was reached once with seeded data. Per the 50% context rule, nothing was skipped for context reasons.
