import Foundation

// Group: aggregate (section E): OvertimeCalculator.aggregate, the period totals.
//
// Runs in every pinned environment: the weekly grouping depends on the first weekday and the
// minimal days in the first week, and the daylight-saving days matter in Israel.
// Weekly hours are multiples of 0.25, so the weekly excess adds exactly whatever order Swift's
// Dictionary happens to visit the weeks in (see docs/ARCHITECTURE.md).

private struct AggregateInput: Encodable {
    let settings: SettingsSpec
    let sessions: [SessionSpec]
}

private struct AggregateCase: Encodable {
    let id: String
    let note: String
    let input: AggregateInput
    let output: BreakdownOut
}

func writeAggregate(_ ctx: GoldenContext) throws {
    var cases: [AggregateCase] = []
    var nextSession = 1

    func add(_ id: String, _ note: String, settings spec: SettingsSpec = SettingsSpec(), sessions: [SessionSpec]) {
        let built = sessions.map { $0.build() }
        let result = OvertimeCalculator.aggregate(sessions: built, settings: spec.build(calendar: ctx.calendar), calendar: ctx.calendar)
        cases.append(AggregateCase(id: id, note: note, input: AggregateInput(settings: spec, sessions: sessions), output: BreakdownOut(result)))
    }

    func shift(_ y: Int, _ m: Int, _ d: Int, hour: Int = 8, hours: Double, breakMinutes: Int = 0,
               dayType: DayType = .regular, night: Bool = false, open: Bool = false) -> SessionSpec {
        let start = ctx.local(y, m, d, hour)
        defer { nextSession += 1 }
        return ctx.session(nextSession, from: start,
                           to: open ? nil : start.addingTimeInterval((hours * 3600).rounded()),
                           breakMinutes: breakMinutes, dayType: dayType, night: night)
    }

    /// One shift per entry on consecutive local days, starting at the given date.
    func days(from y: Int, _ m: Int, _ d: Int, _ hours: [Double], dayType: DayType = .regular) -> [SessionSpec] {
        let start = ctx.local(y, m, d, 12)
        return hours.enumerated().map { offset, h in
            let day = ctx.calendar.date(byAdding: .day, value: offset, to: start)!
            let c = ctx.calendar.dateComponents([.year, .month, .day], from: day)
            return shift(c.year!, c.month!, c.day!, hours: h, dayType: dayType)
        }
    }

    // 2026-06-07 is a Sunday and 2026-06-08 a Monday: the same hours fall in one week or two
    // depending on the environment's first weekday.
    let weeks: [(String, [Double])] = [
        ("41h", [8, 8, 8, 8, 9]),
        ("42h", [8, 8, 8, 9, 9]),
        ("42.5h", [8, 8, 8.5, 9, 9]),
        ("50h", [10, 10, 10, 10, 10]),
        ("54h", [9, 9, 9, 9, 9, 9]),
        ("60h", [10, 10, 10, 10, 10, 10])
    ]
    for (name, hours) in weeks {
        for (label, startDay) in [("monday", 8), ("sunday", 7)] {
            for cap in [0.0, 12.0, 24.0] {
                var spec = SettingsSpec()
                spec.weeklyOvertimeCapHours = cap
                add("week-\(name)-\(label)-cap\(Int(cap))", "one week of \(name), starting on a \(label), weekly cap \(Int(cap))h",
                    settings: spec, sessions: days(from: 2026, 6, startDay, hours))
            }
        }
    }

    // Daily overtime already covers the whole weekly excess: nothing is promoted.
    add("daily-overtime-covers-excess", "4 x 12h = 48h: excess 6h, daily overtime 13.6h", sessions: days(from: 2026, 6, 8, [12, 12, 12, 12]))
    add("daily-overtime-equals-excess", "weekly excess exactly equals the daily overtime",
        settings: { var s = SettingsSpec(); s.standardDayHours = 8; s.ot125HoursCap = 2; s.weeklyStandardHours = 40; return s }(),
        sessions: days(from: 2026, 6, 8, [10, 10, 10, 10]))
    add("below-weekly-standard", "30h in a week", sessions: days(from: 2026, 6, 8, [10, 10, 10]))
    add("no-sessions", "an empty period", sessions: [])

    // Other weekly standards.
    for standard in [1.0, 36.0, 60.0] {
        var spec = SettingsSpec()
        spec.weeklyStandardHours = standard
        add("weekly-standard-\(Int(standard))", "weekly standard \(standard)h, a 50h week", settings: spec,
            sessions: days(from: 2026, 6, 8, [10, 10, 10, 10, 10]))
    }

    // Rounding of the monetary totals: halves go away from zero.
    for rate in [0.005, 0.015, 12.345, 33.335, 61.35, 99.995, 123.45] {
        var spec = SettingsSpec()
        spec.hourlyRate = rate
        spec.dailyGasAllowance = 0
        add("rounding-rate\(rate)-weekly", "a 54h week at rate \(rate)", settings: spec, sessions: days(from: 2026, 6, 8, [9, 9, 9, 9, 9, 9]))
        add("rounding-rate\(rate)-daily", "a 30h week at rate \(rate): no weekly promotion", settings: spec, sessions: days(from: 2026, 6, 8, [10, 10, 10]))
    }
    var oddGas = SettingsSpec()
    oddGas.dailyGasAllowance = 33.335
    add("rounding-gas-33.335", "an allowance that rounds on its own", settings: oddGas, sessions: days(from: 2026, 6, 8, [9, 9, 9]))

    // Open shifts are excluded.
    add("open-shift-excluded", "a running shift next to a 54h week",
        sessions: days(from: 2026, 6, 8, [9, 9, 9, 9, 9, 9]) + [shift(2026, 6, 14, hours: 0, open: true)])
    add("only-open-shift", "nothing is completed", sessions: [shift(2026, 6, 8, hours: 0, open: true)])

    // A shift that crosses the week boundary belongs to the week of its clock-in.
    add("night-shift-across-week-end", "Saturday 22:00 to Sunday 06:00 after a 50h week",
        sessions: days(from: 2026, 6, 8, [10, 10, 10, 10, 10]) + [shift(2026, 6, 13, hour: 22, hours: 8, night: true)])
    add("night-shift-across-sunday", "Sunday 22:00 to Monday 06:00",
        sessions: days(from: 2026, 6, 8, [10, 10, 10, 10, 10]) + [shift(2026, 6, 14, hour: 22, hours: 8, night: true)])
    add("shifts-either-side-of-the-boundary", "two weeks of 30h and 24h meeting at the week change",
        sessions: days(from: 2026, 6, 4, [10, 10, 10]) + days(from: 2026, 6, 8, [8, 8, 8]))
    add("spring-change-week", "a week containing the Israeli spring change (Friday 27 March 2026)",
        sessions: days(from: 2026, 3, 22, [9, 9, 9, 9, 9, 9]))
    add("autumn-change-week", "a week containing the Israeli autumn change (Sunday 25 October 2026)",
        sessions: days(from: 2026, 10, 25, [9, 9, 9, 9, 9, 9]) + [shift(2026, 10, 24, hour: 22, hours: 8, night: true)])

    // Year boundaries (the week-of-year year differs from the calendar year).
    add("year-end-2026", "6 x 10h from Monday 28 Dec 2026 to Saturday 2 Jan 2027", sessions: days(from: 2026, 12, 28, [10, 10, 10, 10, 10, 10]))
    add("year-end-2026-sunday", "6 x 10h from Sunday 27 Dec 2026 to Friday 1 Jan 2027", sessions: days(from: 2026, 12, 27, [10, 10, 10, 10, 10, 10]))
    add("year-end-2025", "6 x 10h from Monday 29 Dec 2025 to Saturday 3 Jan 2026", sessions: days(from: 2025, 12, 29, [10, 10, 10, 10, 10, 10]))
    add("three-weeks-across-new-year", "three weeks from 21 Dec 2026 to 10 Jan 2027",
        sessions: days(from: 2026, 12, 21, [9, 9, 9, 9, 9, 9, 0.25]) + days(from: 2026, 12, 28, [9, 9, 9, 9, 9, 9, 0.5]) + days(from: 2027, 1, 4, [9, 9, 9, 9, 9, 9]))

    // Breaks reduce the paid hours that count toward the week.
    add("breaks-reduce-weekly-hours", "six 10h shifts with a 30 minute break each: 57h paid",
        sessions: (8...13).map { shift(2026, 6, $0, hours: 10, breakMinutes: 30) })

    // Day types in a long week.
    add("rest-day-in-a-long-week", "Saturday rest-day work after a 50h week",
        sessions: days(from: 2026, 6, 8, [10, 10, 10, 10, 10]) + [shift(2026, 6, 13, hours: 8, dayType: .restDay)])
    add("holiday-in-a-long-week", "a holiday shift among 9h days",
        sessions: days(from: 2026, 6, 8, [9, 9, 9, 9, 9]) + [shift(2026, 6, 13, hours: 9, dayType: .holiday)])
    add("sick-days-in-a-week", "sick days carry no hours toward the week",
        sessions: days(from: 2026, 6, 8, [9, 9, 9, 9]) + days(from: 2026, 6, 12, [8.6, 8.6], dayType: .sick))
    add("two-shifts-in-a-day", "split shifts in a 54h week",
        sessions: (8...13).flatMap { [shift(2026, 6, $0, hour: 6, hours: 4.5), shift(2026, 6, $0, hour: 12, hours: 4.5)] })

    // Several unrelated weeks in one period.
    var longPeriod: [SessionSpec] = []
    for (index, hours) in [41.0, 42.5, 50.0, 30.0].enumerated() {
        let per = hours / 5
        longPeriod += days(from: 2026, 6, 1 + 7 * index, [per, per, per, per, per])
    }
    add("four-weeks", "four weeks of 41, 42.5, 50 and 30 hours", sessions: longPeriod)

    // Tax profiles shift net pay only.
    var family = SettingsSpec()
    family.maritalStatus = "married"
    family.hasChildren = true
    family.numberOfChildren = 3
    add("family-profile", "married, three children, 54h week", settings: family, sessions: days(from: 2026, 6, 8, [9, 9, 9, 9, 9, 9]))
    var zeroRate = SettingsSpec()
    zeroRate.hourlyRate = 0
    add("zero-rate", "rate 0: only the allowance", settings: zeroRate, sessions: days(from: 2026, 6, 8, [9, 9, 9, 9, 9, 9]))

    try ctx.write(group: "aggregate", cases: cases)
}
