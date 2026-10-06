# Tax constants and annual checklist

The estimated net pay is computed by `IsraeliTaxEstimator` and `TaxCreditPointsCalculator` on iOS, and ported literally to `core-model` here. These are **planning estimates, not a payroll calculation**.

## Status

- **Source:** copied from the iOS app at commit `5a4f72c986de3e1be6ca68e55d27ab32f0fee223` (2026-10-01).
- **Last verified against official publications:** *not yet verified.* The iOS code does not state the tax year of the brackets; only the credit-point value is labeled "2025 baseline".
- Both apps must always carry **identical** values. The golden tests (generated from iOS) fail if they drift apart.

## Constants (as in the iOS code)

### Income tax: monthly brackets (marginal rates)

| Up to (monthly gross, ILS) | Rate |
|---|---|
| 7,010 | 10% |
| 10,060 | 14% |
| 16,150 | 20% |
| 22,440 | 31% |
| 46,690 | 35% |
| 60,130 | 47% |
| above 60,130 | 50% |

### National Insurance and Health Tax

Threshold between the reduced and full rate: **7,522 ILS per month**.

| | Up to threshold | Above threshold |
|---|---|---|
| National Insurance | 0.4% | 7% |
| National Insurance, retirement age | 0.04% | 0.87% |
| Health Tax | 3.1% | 5% |

Health Tax uses the same rates at every age in the iOS code.

### Credit points

- Base: **2.25** points for a resident.
- Children: **+1 point per child** when `hasChildren` is set (`numberOfChildren`, 0 to 15).
- Married and the spouse is not employed: **+0.5**.
- Value of one point: **242 ILS per month** (labeled "2025 baseline").
- Credits reduce income tax only: `incomeTax = max(0, progressiveTax - credits)`.

### Other constants that affect the estimate

- Average working days per month: **21.67** (spreads monthly figures onto one day).
- Retirement age: **67**, one figure for everyone (age from `birthDate`; unknown means not retired).

## How a day's net is estimated

1. Scale the day's gross to a monthly profile: `monthlyGross = dailyGross × 21.67`.
2. Compute monthly income tax, National Insurance, and Health Tax on it.
3. `net = max(0, dailyGross − (monthly deductions ÷ 21.67))`.

## Annual checklist (every January)

- [ ] Check the **income tax brackets** and the **value of a credit point** on the Israel Tax Authority site.
- [ ] Check the **National Insurance and Health Tax** rates and the reduced-rate threshold on the National Insurance Institute site.
- [ ] Check the **retirement age** rules and the reduced National Insurance rates for retirees.
- [ ] Check whether the **standard day length** (8.6 h, 7 h for night) or the weekly 42 h changed in law.
- [ ] Update the constants in the **iOS app** first.
- [ ] Regenerate the golden files from iOS (`golden/README.md`) and commit them with the iOS commit SHA.
- [ ] Update the constants here in `core-model` so every golden test passes.
- [ ] Update this file: the values above, the source commit, and **Last verified**.
- [ ] Ship both apps in the same release window.
