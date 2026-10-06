import Foundation

// Groups: calendar_ops, night_shift, clock_pair, break_rounding, default_break, paid_elapsed,
//         period_payroll, period_weeks, period_misc, daily_hours_week.
//
// Anything that depends on the calendar runs in every pinned environment. The daylight-saving
// dates matter in Israel; Moscow and UTC are the controls without daylight saving.

// MARK: - calendar_ops: day boundaries, wall-clock times and expected shifts around daylight saving

private struct SetHourRow: Encodable {
    let hour: Int
    let minute: Int
    /// Absent when Foundation returns nil.
    let instant: String?
}

private struct ExpectedShiftRow: Encodable {
    let startHour: Int
    let startMinute: Int
    let clockIn: String
    let clockOut: String
}

private struct CalendarOpsOutput: Encodable {
    let startOfDay: String
    /// `startOfDay` plus one calendar day.
    let nextStartOfDay: String
    /// Real hours between the two (23, 24 or 25).
    let dayLengthHours: GoldenDouble
    let setHour: [SetHourRow]
    let expectedShift: [ExpectedShiftRow]
}

private struct CalendarOpsCase: Encodable {
    let id: String
    let day: String
    let output: CalendarOpsOutput
}

func writeCalendarOps(_ ctx: GoldenContext) throws {
    // The Israeli spring-forward Fridays (note 2028 and 2029: not the last Friday of March),
    // the autumn Sundays, their neighbours, and ordinary days as a control.
    let days: [(Int, Int, Int)] = [
        (2026, 6, 10),
        (2025, 3, 28), (2025, 10, 26),
        (2026, 3, 26), (2026, 3, 27), (2026, 3, 28),
        (2026, 10, 24), (2026, 10, 25), (2026, 10, 26),
        (2027, 3, 26), (2027, 10, 31),
        (2028, 3, 24), (2028, 3, 31), (2028, 10, 29),
        (2029, 3, 23), (2029, 3, 30), (2029, 10, 28)
    ]
    let wallTimes: [(Int, Int)] = [(0, 0), (1, 0), (1, 30), (2, 0), (2, 30), (3, 0), (22, 0)]
    let shiftStarts: [(Int, Int)] = [(8, 0), (1, 30), (2, 30)]

    var cases: [CalendarOpsCase] = []
    for (year, month, dayOfMonth) in days {
        let start = ctx.calendar.startOfDay(for: ctx.local(year, month, dayOfMonth, 12))
        let next = ctx.calendar.date(byAdding: .day, value: 1, to: start)!

        let setHour = wallTimes.map { hour, minute in
            SetHourRow(
                hour: hour, minute: minute,
                instant: ctx.calendar.date(bySettingHour: hour, minute: minute, second: 0, of: start).map { ISO.string($0) }
            )
        }
        let expected = shiftStarts.map { hour, minute -> ExpectedShiftRow in
            var spec = SettingsSpec()
            spec.expectedShiftStartHour = hour
            spec.expectedShiftStartMinute = minute
            spec.defaultBreakMinutes = 30
            let shift = spec.build(calendar: ctx.calendar).expectedShift(on: start, calendar: ctx.calendar)
            return ExpectedShiftRow(startHour: hour, startMinute: minute, clockIn: ISO.string(shift.clockIn), clockOut: ISO.string(shift.clockOut))
        }

        cases.append(CalendarOpsCase(
            id: ctx.localDay(start),
            day: ctx.localDay(start),
            output: CalendarOpsOutput(
                startOfDay: ISO.string(start),
                nextStartOfDay: ISO.string(next),
                dayLengthHours: GoldenDouble(next.timeIntervalSince(start) / 3600),
                setHour: setHour,
                expectedShift: expected
            )
        ))
    }
    try ctx.write(group: "calendar_ops", cases: cases)
}

// MARK: - night_shift: WorkSession.qualifiesAsNightShift

private struct NightCase: Encodable {
    let id: String
    let note: String
    let clockIn: String
    let clockOut: String
    let qualifies: Bool
}

func writeNightShift(_ ctx: GoldenContext) throws {
    var cases: [NightCase] = []
    func add(_ id: String, _ note: String, from clockIn: Date, to clockOut: Date) {
        cases.append(NightCase(
            id: id, note: note,
            clockIn: ISO.string(clockIn), clockOut: ISO.string(clockOut),
            qualifies: WorkSession.qualifiesAsNightShift(clockIn: clockIn, clockOut: clockOut, calendar: ctx.calendar)
        ))
    }
    func t(_ day: Int, _ hour: Int, _ minute: Int = 0, _ second: Int = 0) -> Date { ctx.local(2026, 6, day, hour, minute, second) }

    add("22-00-to-00-00", "exactly two hours inside the window", from: t(10, 22), to: t(11, 0))
    add("22-00-to-23-59-59", "one second short of two hours", from: t(10, 22), to: t(10, 23, 59, 59))
    add("04-00-to-06-00", "exactly two hours at the end of the window", from: t(10, 4), to: t(10, 6))
    add("04-00-to-05-59-59", "one second short", from: t(10, 4), to: t(10, 5, 59, 59))
    add("05-00-to-07-00", "one hour inside the window", from: t(10, 5), to: t(10, 7))
    add("20-00-to-08-00", "a long overnight shift", from: t(10, 20), to: t(11, 8))
    add("06-00-to-22-00", "a full day shift", from: t(10, 6), to: t(10, 22))
    add("14-00-to-22-00", "evening shift ending at the window start", from: t(10, 14), to: t(10, 22))
    add("21-00-to-23-00", "one hour inside", from: t(10, 21), to: t(10, 23))
    add("21-00-to-00-00", "two hours inside", from: t(10, 21), to: t(11, 0))
    add("22-30-to-00-30", "two hours", from: t(10, 22, 30), to: t(11, 0, 30))
    add("23-00-to-01-00", "two hours", from: t(10, 23), to: t(11, 1))
    add("00-00-to-02-00", "two hours after midnight", from: t(10, 0), to: t(10, 2))
    add("05-30-to-06-30", "thirty minutes inside", from: t(10, 5, 30), to: t(10, 6, 30))
    add("equal-times", "clock-out equals clock-in", from: t(10, 23), to: t(10, 23))
    add("out-before-in", "clock-out before clock-in", from: t(10, 23), to: t(10, 22))
    add("two-windows", "a 43 hour span touching two windows", from: t(10, 4), to: t(11, 23))

    // Around daylight saving. The window is 22:00 plus 8 elapsed hours, not 06:00 on the wall clock.
    func d(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int = 0) -> Date { ctx.local(year, month, day, hour, minute) }
    add("dst-spring-2026-overnight", "22:00 to 06:00 across the spring change (7 real hours)", from: d(2026, 3, 26, 22), to: d(2026, 3, 27, 6))
    add("dst-spring-2026-05-to-08", "05:00 to 08:00 on the change day: the window reaches 07:00 on the wall clock", from: d(2026, 3, 27, 5), to: d(2026, 3, 27, 8))
    add("dst-spring-2026-06-to-08", "06:00 to 08:00 on the change day", from: d(2026, 3, 27, 6), to: d(2026, 3, 27, 8))
    add("dst-spring-2026-04-to-06", "04:00 to 06:00 on the change day", from: d(2026, 3, 27, 4), to: d(2026, 3, 27, 6))
    add("dst-autumn-2026-overnight", "22:00 to 06:00 across the autumn change (9 real hours)", from: d(2026, 10, 24, 22), to: d(2026, 10, 25, 6))
    add("dst-autumn-2026-04-to-06", "04:00 to 06:00 on the 25 hour day", from: d(2026, 10, 25, 4), to: d(2026, 10, 25, 6))
    add("dst-autumn-2026-05-to-07", "05:00 to 07:00 on the 25 hour day", from: d(2026, 10, 25, 5), to: d(2026, 10, 25, 7))
    add("dst-autumn-2026-03-to-05", "03:00 to 05:00 on the 25 hour day", from: d(2026, 10, 25, 3), to: d(2026, 10, 25, 5))
    add("dst-spring-2028-overnight", "22:00 to 06:00 across the 2028 spring change (a Friday that is not the last)", from: d(2028, 3, 23, 22), to: d(2028, 3, 24, 6))
    add("dst-spring-2028-05-to-08", "05:00 to 08:00 on the 2028 change day", from: d(2028, 3, 24, 5), to: d(2028, 3, 24, 8))
    add("dst-evening-before-spring-2026", "21:00 to 23:30 the evening before", from: d(2026, 3, 26, 21), to: d(2026, 3, 26, 23, 30))

    try ctx.write(group: "night_shift", cases: cases)
}

// MARK: - clock_pair: WorkSession.resolveClockPair

private struct PairCase: Encodable {
    let id: String
    let note: String
    let clockIn: String
    let clockOut: String
    let resolvedClockIn: String
    let resolvedClockOut: String
}

func writeClockPair(_ ctx: GoldenContext) throws {
    var cases: [PairCase] = []
    func add(_ id: String, _ note: String, from clockIn: Date, to clockOut: Date) {
        let result = WorkSession.resolveClockPair(clockIn: clockIn, clockOut: clockOut, calendar: ctx.calendar)
        cases.append(PairCase(
            id: id, note: note,
            clockIn: ISO.string(clockIn), clockOut: ISO.string(clockOut),
            resolvedClockIn: ISO.string(result.clockIn), resolvedClockOut: ISO.string(result.clockOut)
        ))
    }
    func t(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date { ctx.local(2026, 6, day, hour, minute) }

    add("normal-day-shift", "07:22 to 17:02: unchanged", from: t(10, 7, 22), to: t(10, 17, 2))
    add("swapped-same-day", "17:02 to 07:22 on one date: the OCR swap is undone", from: t(10, 17, 2), to: t(10, 7, 22))
    add("real-overnight", "22:00 to 06:00: a genuine overnight shift", from: t(10, 22), to: t(10, 6))
    add("swapped-plus-a-day", "17:02 to 07:22 the next day (14h20): swapped and shifted, so it is corrected", from: t(10, 17, 2), to: t(11, 7, 22))
    add("long-span-not-swapped", "07:22 to 17:02 two days later: unchanged", from: t(10, 7, 22), to: t(12, 17, 2))
    add("overnight-exactly-12h", "18:00 to 06:00 (12.0h overnight): not greater than 12, so a real overnight", from: t(10, 18), to: t(10, 6))
    add("overnight-just-under-12h", "18:01 to 06:00", from: t(10, 18, 1), to: t(10, 6))
    add("swapped-exactly-3h", "10:00 to 07:00: the swapped gap is exactly 3.0h, which is allowed", from: t(10, 10), to: t(10, 7))
    add("swapped-just-under-3h", "10:00 to 07:01: 2h59, so it is a (very long) overnight", from: t(10, 10), to: t(10, 7, 1))
    add("swapped-just-under-12h", "19:59 to 08:00 the next day: 12h01 span, gap 11h59, corrected", from: t(10, 19, 59), to: t(11, 8))
    add("span-exactly-12h", "08:00 to 20:00: exactly 12.0h, not greater, unchanged", from: t(10, 8), to: t(10, 20))
    add("span-just-over-12h", "08:00 to 20:01: over 12h but the times are not swapped", from: t(10, 8), to: t(10, 20, 1))
    add("equal-times", "identical in and out", from: t(10, 8), to: t(10, 8))
    add("gap-3h-boundary-next-day", "10:00 to 07:00 the next day (21h): swapped gap exactly 3h", from: t(10, 10), to: t(11, 7))
    add("gap-just-under-3h-next-day", "10:00 to 07:01 the next day", from: t(10, 10), to: t(11, 7, 1))

    // Around daylight saving.
    func d(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int = 0) -> Date { ctx.local(year, month, day, hour, minute) }
    add("dst-spring-overnight", "22:00 to 06:00 across the spring change", from: d(2026, 3, 26, 22), to: d(2026, 3, 27, 6))
    add("dst-spring-swapped", "17:02 to 07:22 on the change day", from: d(2026, 3, 27, 17, 2), to: d(2026, 3, 27, 7, 22))
    add("dst-autumn-swapped", "17:02 to 07:22 on the 25 hour day", from: d(2026, 10, 25, 17, 2), to: d(2026, 10, 25, 7, 22))
    add("dst-autumn-overnight", "22:00 to 06:00 across the autumn change", from: d(2026, 10, 24, 22), to: d(2026, 10, 25, 6))
    add("dst-spring-swapped-plus-a-day", "17:02 on the change day to 07:22 the next day", from: d(2026, 3, 27, 17, 2), to: d(2026, 3, 28, 7, 22))

    try ctx.write(group: "clock_pair", cases: cases)
}

// MARK: - break_rounding: recording breaks and folding them into break minutes

private struct BreakAction: Encodable {
    /// "startBreak", "endBreak" or "closeOpenBreak".
    let kind: String
    let at: String
    let deductFromPay: Bool
}

private struct BreakInput: Encodable {
    let session: SessionSpec
    let action: BreakAction
}

private struct BreakOutput: Encodable {
    /// The Bool the action returned; absent for `closeOpenBreak`, which returns nothing.
    let returned: Bool?
    let breakMinutes: Int
    let breaks: [BreakSpec]
}

private struct BreakCase: Encodable {
    let id: String
    let note: String
    let input: BreakInput
    let output: BreakOutput
}

func writeBreakRounding(_ ctx: GoldenContext) throws {
    func t(_ hour: Int, _ minute: Int = 0, _ second: Int = 0) -> Date { ctx.local(2026, 6, 10, hour, minute, second) }
    var cases: [BreakCase] = []

    func apply(_ id: String, _ note: String, session spec: SessionSpec, kind: String, at: Date, deduct: Bool = true) {
        var session = spec.build()
        var returned: Bool?
        switch kind {
        case "startBreak": returned = session.startBreak(at: at)
        case "endBreak": returned = session.endBreak(at: at, deductFromPay: deduct)
        case "closeOpenBreak": session.closeOpenBreak(at: at, deductFromPay: deduct)
        default: fatalError("unknown action \(kind)")
        }
        cases.append(BreakCase(
            id: id, note: note,
            input: BreakInput(session: spec, action: BreakAction(kind: kind, at: ISO.string(at), deductFromPay: deduct)),
            output: BreakOutput(
                returned: returned,
                breakMinutes: session.breakMinutes,
                breaks: session.breaks.map { BreakSpec(start: ISO.string($0.start), end: $0.end.map { ISO.string($0) }) }
            )
        ))
    }

    func openShift(breaks: [BreakSpec] = [], breakMinutes: Int = 0) -> SessionSpec {
        ctx.session(1, from: t(8), to: nil, breakMinutes: breakMinutes, breaks: breaks)
    }

    // One break of a given length; the total is rounded to minutes, halves away from zero.
    for seconds in [29, 30, 31, 89, 90, 91, 149, 150, 151, 3600] {
        let start = t(10)
        let end = start.addingTimeInterval(TimeInterval(seconds))
        apply("end-break-\(seconds)s", "a single break of \(seconds) seconds",
              session: openShift(breaks: [BreakSpec(start: ISO.string(start), end: nil)]),
              kind: "endBreak", at: end)
    }

    // Three breaks of 90 seconds: 270 seconds is 4.5 minutes, which rounds up to 5.
    let first = BreakSpec(start: ISO.string(t(9)), end: ISO.string(t(9, 1, 30)))
    let second = BreakSpec(start: ISO.string(t(10)), end: ISO.string(t(10, 1, 30)))
    let third = BreakSpec(start: ISO.string(t(11)), end: nil)
    apply("end-break-three-breaks-270s", "270 seconds in total is 4.5 minutes",
          session: openShift(breaks: [first, second, third]), kind: "endBreak", at: t(11, 1, 30))
    apply("end-break-not-deducted", "a workplace that pays for breaks: break minutes stay as they were",
          session: openShift(breaks: [third], breakMinutes: 7), kind: "endBreak", at: t(11, 20), deduct: false)
    apply("end-break-nothing-open", "no open break: returns false and changes nothing",
          session: openShift(breaks: [first], breakMinutes: 2), kind: "endBreak", at: t(12))
    apply("end-break-before-its-start", "ending before the start clamps the end to the start",
          session: openShift(breaks: [BreakSpec(start: ISO.string(t(11)), end: nil)]), kind: "endBreak", at: t(10, 30))

    apply("close-open-break-deducted", "an open break is closed when the shift is closed",
          session: openShift(breaks: [BreakSpec(start: ISO.string(t(12)), end: nil)]), kind: "closeOpenBreak", at: t(12, 20))
    apply("close-open-break-not-deducted", "same, at a workplace that pays for breaks",
          session: openShift(breaks: [BreakSpec(start: ISO.string(t(12)), end: nil)], breakMinutes: 3), kind: "closeOpenBreak", at: t(12, 20), deduct: false)
    apply("close-open-break-none", "no open break: nothing happens",
          session: openShift(breaks: [first], breakMinutes: 9), kind: "closeOpenBreak", at: t(13))

    apply("start-break-ok", "starts a break on an open shift", session: openShift(), kind: "startBreak", at: t(10))
    apply("start-break-already-on-break", "a second break cannot start while one is running",
          session: openShift(breaks: [third]), kind: "startBreak", at: t(12))
    apply("start-break-before-clock-in", "a break cannot start before the shift", session: openShift(), kind: "startBreak", at: t(7, 59))
    apply("start-break-closed-shift", "a closed shift cannot take a break",
          session: ctx.session(1, from: t(8), to: t(16)), kind: "startBreak", at: t(10))
    apply("start-break-at-clock-in", "starting exactly at clock-in is allowed", session: openShift(), kind: "startBreak", at: t(8))

    try ctx.write(group: "break_rounding", cases: cases)
}

// MARK: - default_break: WorkSession.applyDefaultBreakIfNeeded

private struct DefaultBreakInput: Encodable {
    let session: SessionSpec
    let defaultBreakMinutes: Int
    let breaksArePaid: Bool
}

private struct DefaultBreakCase: Encodable {
    let id: String
    let input: DefaultBreakInput
    let breakMinutes: Int
}

func writeDefaultBreak(_ ctx: GoldenContext) throws {
    var cases: [DefaultBreakCase] = []
    let start = ctx.local(2026, 6, 10, 8)

    // Just under, exactly at, just over, and well over the 6 hour threshold.
    for seconds in [21_599, 21_600, 21_601, 28_800] {
        for defaultMinutes in [0, 30] {
            for paid in [false, true] {
                for existing in [0, 15] {
                    for recorded in [false, true] {
                        let breaks = recorded
                            ? [BreakSpec(start: ISO.string(ctx.local(2026, 6, 10, 10)), end: ISO.string(ctx.local(2026, 6, 10, 10, 10)))]
                            : []
                        let spec = ctx.session(1, from: start, to: start.addingTimeInterval(TimeInterval(seconds)),
                                               breakMinutes: existing, breaks: breaks)
                        var session = spec.build()
                        var settingsSpec = SettingsSpec()
                        settingsSpec.defaultBreakMinutes = defaultMinutes
                        settingsSpec.breaksArePaid = paid
                        session.applyDefaultBreakIfNeeded(settings: settingsSpec.build(calendar: ctx.calendar))
                        cases.append(DefaultBreakCase(
                            id: "\(seconds)s-default\(defaultMinutes)-paid\(paid)-existing\(existing)-recorded\(recorded)",
                            input: DefaultBreakInput(session: spec, defaultBreakMinutes: defaultMinutes, breaksArePaid: paid),
                            breakMinutes: session.breakMinutes
                        ))
                    }
                }
            }
        }
    }
    try ctx.write(group: "default_break", cases: cases)
}

// MARK: - paid_elapsed: paid time so far, recorded break time, and effective hours

private struct PaidElapsedInput: Encodable {
    let session: SessionSpec
    let now: String
    let breaksArePaid: Bool
}

private struct PaidElapsedOutput: Encodable {
    let paidElapsedSeconds: GoldenDouble
    let recordedBreakSeconds: GoldenDouble
    let totalHours: GoldenDouble
    let effectiveHours: GoldenDouble
}

private struct PaidElapsedCase: Encodable {
    let id: String
    let note: String
    let input: PaidElapsedInput
    let output: PaidElapsedOutput
}

func writeCompletedPaidElapsed(_ ctx: GoldenContext) throws {
    func t(_ hour: Int, _ minute: Int = 0, _ second: Int = 0) -> Date { ctx.local(2026, 6, 10, hour, minute, second) }
    var cases: [PaidElapsedCase] = []

    func add(_ id: String, _ note: String, session spec: SessionSpec, now: Date, paid: Bool) {
        let session = spec.build()
        cases.append(PaidElapsedCase(
            id: id, note: note,
            input: PaidElapsedInput(session: spec, now: ISO.string(now), breaksArePaid: paid),
            output: PaidElapsedOutput(
                paidElapsedSeconds: GoldenDouble(session.paidElapsedSeconds(now: now, breaksArePaid: paid)),
                recordedBreakSeconds: GoldenDouble(session.recordedBreakSeconds(now: now)),
                totalHours: GoldenDouble(session.totalHours),
                effectiveHours: GoldenDouble(session.effectiveHours)
            )
        ))
    }

    let closedBreak = BreakSpec(start: ISO.string(t(10)), end: ISO.string(t(10, 20)))
    let runningBreak = BreakSpec(start: ISO.string(t(12)), end: nil)

    for paid in [false, true] {
        let tag = paid ? "paid" : "unpaid"
        add("open-no-breaks-\(tag)", "open shift, no breaks", session: ctx.session(1, from: t(8), to: nil), now: t(13), paid: paid)
        add("open-closed-break-\(tag)", "one finished 20 minute break",
            session: ctx.session(1, from: t(8), to: nil, breaks: [closedBreak]), now: t(13), paid: paid)
        add("open-running-break-\(tag)", "a break still running at 12:30",
            session: ctx.session(1, from: t(8), to: nil, breaks: [closedBreak, runningBreak]), now: t(12, 30), paid: paid)
        add("open-now-before-clock-in-\(tag)", "the clock is before clock-in: clamped at zero",
            session: ctx.session(1, from: t(8), to: nil), now: t(7), paid: paid)
        add("closed-with-breaks-\(tag)", "closed shift: its own clock-out is used, not `now`",
            session: ctx.session(1, from: t(8), to: t(16), breaks: [closedBreak]), now: t(23), paid: paid)
        add("closed-break-minutes-\(tag)", "closed shift with a 45 minute unpaid break",
            session: ctx.session(1, from: t(8), to: t(17), breakMinutes: 45), now: t(23), paid: paid)
        add("closed-negative-length-\(tag)", "clock-out before clock-in",
            session: ctx.session(1, from: t(16), to: t(8)), now: t(23), paid: paid)
    }

    try ctx.write(group: "paid_elapsed", cases: cases)
}

// MARK: - period_payroll, period_weeks, period_misc, daily_hours_week: HistoryPeriodHelper

private struct PeriodOutput: Encodable {
    let start: String
    let end: String
    let labelMonth: String
}

private struct PayrollCase: Encodable {
    let id: String
    /// "forMonthAnchor" or "containing".
    let mode: String
    let anchor: String
    let startDay: Int
    let output: PeriodOutput
}

func writePeriodPayroll(_ ctx: GoldenContext) throws {
    var cases: [PayrollCase] = []
    func output(_ p: PayrollPeriod) -> PeriodOutput {
        PeriodOutput(start: ctx.localDay(p.start), end: ctx.localDay(p.end), labelMonth: ctx.localDay(p.labelMonth))
    }

    let anchors: [(Int, Int, Int)] = [(2026, 1, 15), (2026, 2, 10), (2028, 2, 29), (2026, 3, 31), (2026, 12, 31)]
    for (y, m, d) in anchors {
        for startDay in [1, 10, 28, 31, 0] {
            let anchor = ctx.local(y, m, d, 12)
            let period = HistoryPeriodHelper.payrollPeriod(forMonthAnchor: anchor, startDay: startDay, calendar: ctx.calendar)
            cases.append(PayrollCase(id: "anchor-\(ctx.localDay(anchor))-start\(startDay)", mode: "forMonthAnchor",
                                     anchor: ctx.localDay(anchor), startDay: startDay, output: output(period)))
        }
    }

    // The period that contains a date, around month starts and the daylight-saving days.
    let probes: [(Int, Int, Int)] = [
        (2026, 1, 5), (2026, 1, 10), (2026, 1, 31), (2026, 2, 9), (2026, 2, 10), (2026, 3, 27),
        (2026, 3, 9), (2026, 10, 25), (2026, 10, 9), (2028, 3, 1), (2026, 12, 31), (2027, 1, 1)
    ]
    for (y, m, d) in probes {
        for startDay in [1, 10, 28] {
            let day = ctx.local(y, m, d, 12)
            let period = HistoryPeriodHelper.payrollPeriod(containing: day, startDay: startDay, calendar: ctx.calendar)
            cases.append(PayrollCase(id: "containing-\(ctx.localDay(day))-start\(startDay)", mode: "containing",
                                     anchor: ctx.localDay(day), startDay: startDay, output: output(period)))
        }
    }
    try ctx.write(group: "period_payroll", cases: cases)
}

private struct WeekDayOut: Encodable {
    let day: String
    let inPeriod: Bool
}

private struct WeeksCase: Encodable {
    let id: String
    let anchor: String
    let startDay: Int
    let weeks: [[WeekDayOut]]
}

func writePeriodWeeks(_ ctx: GoldenContext) throws {
    var cases: [WeeksCase] = []
    // Months that start on different weekdays, a leap February, and the daylight-saving months.
    let anchors: [(Int, Int, Int)] = [(2026, 1, 15), (2026, 2, 15), (2028, 2, 15), (2026, 3, 15), (2026, 10, 15), (2026, 6, 15)]
    for (y, m, d) in anchors {
        for startDay in [1, 10] {
            let anchor = ctx.local(y, m, d, 12)
            let period = HistoryPeriodHelper.payrollPeriod(forMonthAnchor: anchor, startDay: startDay, calendar: ctx.calendar)
            let weeks = HistoryPeriodHelper.weekRows(for: period, calendar: ctx.calendar).map { week in
                week.days.map { WeekDayOut(day: ctx.localDay($0.date), inPeriod: $0.isInPeriod) }
            }
            cases.append(WeeksCase(id: "weeks-\(ctx.localDay(anchor))-start\(startDay)", anchor: ctx.localDay(anchor), startDay: startDay, weeks: weeks))
        }
    }
    try ctx.write(group: "period_weeks", cases: cases)
}

private struct GenericCase: Encodable {
    let id: String
    /// What was called; the arguments and results are plain text.
    let op: String
    let args: [String]
    let result: [String]
}

func writePeriodMisc(_ ctx: GoldenContext) throws {
    var cases: [GenericCase] = []
    func add(_ op: String, _ args: [String], _ result: [String]) {
        cases.append(GenericCase(id: "\(op)(\(args.joined(separator: ",")))", op: op, args: args, result: result))
    }
    func day(_ y: Int, _ m: Int, _ d: Int) -> Date { ctx.local(y, m, d, 12) }
    func text(_ date: Date) -> String { ctx.localDay(date) }

    for value in [-5, 0, 1, 10, 28, 29, 31, 100] {
        add("normalizedStartDay", ["\(value)"], ["\(HistoryPeriodHelper.normalizedStartDay(value))"])
    }

    // Calendar-day ranges, across both daylight-saving changes.
    let ranges: [((Int, Int, Int), (Int, Int, Int))] = [
        ((2026, 3, 25), (2026, 3, 29)), ((2026, 10, 23), (2026, 10, 27)), ((2028, 3, 22), (2028, 3, 26)),
        ((2026, 6, 10), (2026, 6, 10)), ((2026, 6, 12), (2026, 6, 10)), ((2026, 12, 29), (2027, 1, 3))
    ]
    for (from, through) in ranges {
        let list = HistoryPeriodHelper.days(from: day(from.0, from.1, from.2), through: day(through.0, through.1, through.2), calendar: ctx.calendar)
        add("days", [text(day(from.0, from.1, from.2)), text(day(through.0, through.1, through.2))], list.map(text))
    }

    // Start of the week for each weekday, around a year end and a leap day.
    let weekProbes: [(Int, Int, Int)] = [
        (2026, 6, 10), (2026, 6, 11), (2026, 6, 12), (2026, 6, 13), (2026, 6, 14), (2026, 6, 15), (2026, 6, 16),
        (2026, 1, 1), (2025, 12, 31), (2028, 2, 29), (2026, 3, 27), (2026, 10, 25)
    ]
    for (y, m, d) in weekProbes {
        let date = day(y, m, d)
        add("startOfWeek", [text(date)], [text(HistoryPeriodHelper.startOfWeek(containing: date, calendar: ctx.calendar))])
    }

    for (y, m, d) in [(2026, 2, 10), (2028, 2, 10), (2026, 3, 10), (2026, 10, 10), (2026, 1, 31), (2026, 12, 1)] {
        let date = day(y, m, d)
        let list = HistoryPeriodHelper.daysInMonth(containing: date, calendar: ctx.calendar)
        add("daysInMonth", [text(date)], list.map(text))
    }

    // Month arithmetic clamps to the end of shorter months.
    let shifts: [((Int, Int, Int), Int)] = [
        ((2026, 1, 31), 1), ((2026, 3, 31), -1), ((2028, 1, 31), 1), ((2026, 12, 15), 1),
        ((2026, 1, 15), -1), ((2026, 5, 31), 12), ((2026, 5, 31), -13)
    ]
    for ((y, m, d), by) in shifts {
        let date = day(y, m, d)
        add("shiftMonth", [text(date), "\(by)"], [text(HistoryPeriodHelper.shiftMonth(date, by: by, calendar: ctx.calendar))])
        add("shiftPayrollAnchor", [text(date), "\(by)"], [text(HistoryPeriodHelper.shiftPayrollAnchor(date, by: by, calendar: ctx.calendar))])
    }

    // Hours as HH:MM.
    let clockHours: [Double] = [0, 0.5, 8.6, 8.999, 8.9999, 12.0083, 100, 0.0001, 0.00833, 59.99, 7.5833333]
    for hours in clockHours {
        add("formatHoursClock", ["\(hours)"], [HistoryPeriodHelper.formatHoursClock(hours)])
    }

    try ctx.write(group: "period_misc", cases: cases)
}

private struct WeekHoursCase: Encodable {
    let id: String
    let anchor: String
    let sessions: [SessionSpec]
    let hours: [GoldenDouble]
}

func writeDailyHoursWeek(_ ctx: GoldenContext) throws {
    var cases: [WeekHoursCase] = []
    func t(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date { ctx.local(2026, 6, day, hour, minute) }

    func add(_ id: String, anchor: Date, sessions: [SessionSpec]) {
        let built = sessions.map { $0.build() }
        let hours = HistoryPeriodHelper.dailyHoursForWeek(containing: anchor, sessions: built, calendar: ctx.calendar)
        cases.append(WeekHoursCase(id: id, anchor: ctx.localDay(anchor), sessions: sessions, hours: hours.map(GoldenDouble.init)))
    }

    // 2026-06-07 is a Sunday, 2026-06-13 a Saturday.
    let week: [SessionSpec] = [
        ctx.session(1, from: t(7, 8), to: t(7, 16)),
        ctx.session(2, from: t(8, 8), to: t(8, 12)),
        ctx.session(3, from: t(8, 13), to: t(8, 18, 30)),
        ctx.session(4, from: t(10, 22), to: t(11, 6), night: true),
        ctx.session(5, from: t(12, 9), to: t(12, 14)),
        ctx.session(6, from: t(13, 6), to: t(13, 12), dayType: .restDay),
        ctx.session(7, from: t(14, 8), to: t(14, 16)),
        ctx.session(8, from: t(6, 8), to: t(6, 17)),
        ctx.session(9, from: t(9, 10), to: nil)
    ]
    for anchorDay in [7, 10, 13, 14, 6] {
        add("week-of-\(anchorDay)", anchor: t(anchorDay, 12), sessions: week)
    }
    add("empty-week", anchor: t(10, 12), sessions: [])
    // A week that contains the spring change in Israel.
    let spring: [SessionSpec] = [
        ctx.session(1, from: ctx.local(2026, 3, 26, 22), to: ctx.local(2026, 3, 27, 6), night: true),
        ctx.session(2, from: ctx.local(2026, 3, 29, 8), to: ctx.local(2026, 3, 29, 17)),
        ctx.session(3, from: ctx.local(2026, 3, 22, 8), to: ctx.local(2026, 3, 22, 16))
    ]
    add("spring-change-week", anchor: ctx.local(2026, 3, 27, 12), sessions: spring)

    try ctx.write(group: "daily_hours_week", cases: cases)
}
