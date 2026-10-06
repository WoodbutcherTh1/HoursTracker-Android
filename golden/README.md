# Golden files

The Android pay engine must produce **exactly** the same numbers as the iOS app. Golden files are the proof: a Swift harness runs the real iOS code on a Mac and records inputs and outputs as JSON. The Kotlin tests in `core-model` then compare against them bit for bit.

- `harness/` — Swift package that generates the data.
- `data/` — the generated JSON files and `manifest.json` (the iOS commit SHA and generation time).
- `run.sh` — the only supported way to run the harness.

**iOS source code is never copied into this repository.** `run.sh` links the files listed in `harness/ios-sources.txt` into an ignored folder for the duration of the run and removes the links afterwards. Only the generated data is committed.

## How to regenerate (on a Mac)

You need a Mac with Xcode or the Command Line Tools (`xcode-select --install`), a local checkout of the iOS repository, and this repository.

1. In the **iOS** checkout, switch to the commit you want to test against, and make sure the working tree is clean (`git status` shows nothing). `run.sh` refuses to run otherwise, because the recorded SHA must describe exactly the code that produced the data.
2. In **this** repository, switch to the branch you are working on and pull.
3. Run the harness, passing the path to the iOS checkout:

   ```
   golden/run.sh ~/path/to/HoursTracker
   ```

4. Look at what changed:

   ```
   git status golden/data
   git diff --stat golden/data
   ```

5. Commit and push the data (the script prints the commit command, including the short SHA):

   ```
   git add golden/data
   git commit -m "test(golden): regenerate from iOS <sha7>"
   git push
   ```

## Why the harness runs in three environments

Some iOS functions use `Calendar.current` internally (for example `aggregate` groups days with it, and the week-of-year grouping depends on the first weekday and the minimal days in the first week), and `Calendar.current` follows the device. Passing a calendar as a parameter is therefore not enough.

So `run.sh` runs the harness once per environment, each in its own process with the time zone and the calendar settings pinned:

| Environment | Time zone | First weekday | Purpose |
|---|---|---|---|
| `il` | `Asia/Jerusalem` | as defined by the Israeli locale | the real target; has daylight saving |
| `ru` | `Europe/Moscow` | Monday | week start differs; no daylight saving |
| `utc` | `UTC` | Monday | neutral reference |

At startup the harness checks that `Calendar.current` really has the pinned time zone, first weekday, and minimal days, and **stops with a clear message if it does not**. Each file in `data/` records the environment it was produced in (including the first weekday and minimal days actually read from the system), so the result never depends on the Mac's own region, language, or time zone.

## What only the Mac can settle

Foundation on Linux and on macOS are not the same implementation. The clearest known difference is a wall-clock time that does not exist: asking for 02:30 on the spring daylight-saving day (when 02:00 jumps to 03:00). Linux answers 03:00; macOS may answer 03:30. The data produced on a Mac is the reference, which is why provisional (Linux) output is never committed. The Kotlin side follows whatever the Mac data says.

## Troubleshooting

- **"run this on a Mac"** — `Foundation` on Linux differs from Apple's (notably `Calendar`), so golden data must come from macOS.
- **"uncommitted changes"** — commit or stash the changes in the iOS checkout and run again.
- **"Could not pin the environment"** — the harness could not make this Mac behave like the Israeli, Russian, or UTC environment and refuses to write data that would depend on the Mac's settings. Copy the full message and send it over. Changing System Settings > General > Language & Region > First day of week to "Default" and running again may also help.
- **A Swift build error** — copy the full message and send it over. Do not edit the iOS repository to work around it.

## Known limit: the order of the weekly excess sum

`aggregate` sums the weekly excess hours over a Swift `Dictionary`, whose order is random per process, while the Kotlin port sums in order of first appearance. Floating-point addition is not associative, so the last bits only agree when the weekly totals add exactly. That is why every `aggregate` case uses hours that are multiples of 0.25. Cases with other hours must be compared with a tolerance (1e-9), never bit for bit.

## Data format

- A `Double` is stored as an object with its raw IEEE-754 bits and a readable value: `{"bits": "4021333333333333", "value": 8.6}`. Tests compare the bits.
- An optional value that is `nil` in Swift is **omitted** from the JSON, not written as `null`. Readers treat a missing key as "no value".
- Dates are stored as an ISO-8601 instant plus the explicit time zone identifier used.
- Money strings are compared after removing bidirectional marks and special spaces; digits, currency, and rounding must match exactly. Exact text equality is not required because Apple and Android ship different locale data.
- Cases whose result depends on summation order (weekly overtime) use hours that are multiples of 0.25 for bit-for-bit checks; other inputs are compared with a tolerance of 1e-9.
- `manifest.json` lists every data file and the iOS commit it came from.

The test cases themselves are defined in milestone M1.
