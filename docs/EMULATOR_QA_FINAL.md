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

## Status update (follow-up fixes)

| # | Finding | Status |
|---|---------|--------|
| 1 | Pay period "reversed" | **False positive.** The label is built as start then end; Hebrew is right-to-left, so `01.10 – 31.10` is drawn as `31.10 – 01.10`. The calculator is correct. Invariant test added (`PayrollPeriodOrderTest`, all start days 1-28, all months). |
| 2 | Export month-first dates | Fixed (`dd/MM`) |
| 3 | Footer clipped by tab bar | Fixed (system nav inset added). Export preview was never clipped: it scrolls above a pinned button. |
| 4 | Times not zero-padded | Fixed in History for 24-hour locales |
| 5 | Decimal hours in Export | Fixed (`HH:mm`) |
| 6 | Home month card bare "7" | Open: it is a shift count by design; needs a pluralized unit string in 4 languages |
| 7 | Payslips add control | Open: payslips have no storage yet; a button needs a new feature, not just UI |
| 8 | Onboarding numbers | **Verified correct.** Engine adds the daily fuel allowance (₪35): 8 h x 50 + 35 = 435; 42 h over 5 days = 8.4 h/day x 50 + 35 = 455, x 5 = 2,275 |

## Findings (original)

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

## Flow tests (follow-up run)

| Flow | Result |
|------|--------|
| Clock in | Works. Live timer and net estimate shown; notification pre-prompt appears first ("not now" tested). |
| Break start / end | Works. Timer and estimate freeze during the break, resume after. |
| Clock out | Works only with a **long press**; a plain tap does nothing and there is no on-screen hint. The summary sheet then shows gross, net, break, fuel and deductions. |
| Manual entry (History "+") | Works. New row appears in History and totals add up (2,747.95 + 411.62 + 0.03 = 3,159.60). |
| Settings save | Works. Save button turns active on edit; "Changes saved" confirmation. Rate edited 50 → 55 and restored to 50. |

New observations:
- **Med** — Clock-out needs a long press with no hint; a user who taps will think the button is broken.
- **Low** — While a shift runs, the Clock-out button sits below the fold on a 1080x2400 screen (reachable by scrolling).
- **Verify** — Shift summary reports a "1 min" break for a break of about 20 seconds (possible round-up).
- **Verify** — After saving the manual entry the form stayed on screen for about 3 s before returning to History (slow transition or lag).
- The 1-minute shift row shows net ₪0.03 in History while its summary sheet showed net ₪33.80: the daily fuel allowance is applied once per day, to the manual shift. Consistent with the engine, but surprising to a user.

## Not tested

Clock-in/out and break flows, adding or editing a shift, other export formats (not PDF), the report-language and notes options, and the ID number field. Home was reached once with seeded data. Per the 50% context rule, nothing was skipped for context reasons.
