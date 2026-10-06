import Foundation

// Groups: breakdown_simple, breakdown_day, day_types.

// MARK: - breakdown_simple: OvertimeCalculator.breakdown(totalHours:settings:includeGasAllowance:)

private struct SimpleInput: Encodable {
    let totalHours: Double
    let includeGasAllowance: Bool
    let settings: SettingsSpec
}

private struct SimpleCase: Encodable {
    let id: String
    let input: SimpleInput
    let output: BreakdownOut
}

func writeBreakdownSimple(_ ctx: GoldenContext) throws {
    var cases: [SimpleCase] = []

    func add(_ id: String, hours: Double, includeGas: Bool = true, _ spec: SettingsSpec) {
        let result = OvertimeCalculator.breakdown(
            totalHours: hours,
            settings: spec.build(calendar: ctx.calendar),
            includeGasAllowance: includeGas
        )
        cases.append(SimpleCase(
            id: id,
            input: SimpleInput(totalHours: hours, includeGasAllowance: includeGas, settings: spec),
            output: BreakdownOut(result)
        ))
    }

    // The exact tier boundaries, and a hair either side of them (1e-9 is far below a cent
    // but changes the last bits, which is what proves the arithmetic is the same).
    let hours: [Double] = [0, 0.25, 8, 8.6, 8.6 + 1e-9, 10, 10.6, 10.6 + 1e-9, 12, 24]
    let caps: [Double] = [0, 2, 3]
    let standardDays: [Double] = [7, 8, 8.6, 9]
    let gasAllowances: [Double] = [0, 35]
    let rates: [Double] = [0, 52.7, 100, 123.45]

    for h in hours {
        for cap in caps {
            for std in standardDays {
                for gas in gasAllowances {
                    for rate in rates {
                        var spec = SettingsSpec()
                        spec.ot125HoursCap = cap
                        spec.standardDayHours = std
                        spec.dailyGasAllowance = gas
                        spec.hourlyRate = rate
                        add("h\(h)-cap\(cap)-std\(std)-gas\(gas)-rate\(rate)", hours: h, spec)
                    }
                }
            }
        }
    }

    // includeGasAllowance = false.
    for h in [8.6, 10.6, 12] {
        add("noGas-h\(h)", hours: h, includeGas: false, SettingsSpec())
    }

    // The currency code is carried through untouched.
    for code in ["USD", "EUR", "GBP"] {
        var spec = SettingsSpec()
        spec.currencyCode = code
        add("currency-\(code)", hours: 10, spec)
    }

    // Net pay depends on the tax profile (credit points, retirement age).
    func profile(_ id: String, _ change: (inout SettingsSpec) -> Void) {
        var spec = SettingsSpec()
        change(&spec)
        add("profile-\(id)", hours: 10, spec)
    }
    profile("married-spouse-employed") { $0.maritalStatus = "married"; $0.spouseEmployed = true }
    profile("married-spouse-not-employed") { $0.maritalStatus = "married"; $0.spouseEmployed = false }
    profile("three-children") { $0.hasChildren = true; $0.numberOfChildren = 3 }
    profile("children-ignored-without-flag") { $0.hasChildren = false; $0.numberOfChildren = 3 }
    // Far from the 67th birthday on both sides, because the age is computed from today's date.
    profile("retired-born-1940") { $0.birthDate = "1940-01-01" }
    profile("working-age-born-2000") { $0.birthDate = "2000-01-01" }

    try ctx.write(group: "breakdown_simple", cases: cases)
}

// MARK: - Shared by breakdown_day and day_types

private struct DayInput: Encodable {
    let settings: SettingsSpec
    let sessions: [SessionSpec]
}

private struct DayRow: Encodable {
    let sessionId: String
    let breakdown: BreakdownOut
}

private struct StreakRow: Encodable {
    let day: String
    let number: Int
}

private struct DayOutput: Encodable {
    /// `dayAwareBreakdowns`: grouped by day, in clock-in order.
    let dayAware: [DayRow]
    /// `breakdown(for:in:)` for each input session, in input order.
    let perSession: [DayRow]
    /// `sickStreakDayNumbers`, sorted by day.
    let sickStreaks: [StreakRow]
}

private struct DayCase: Encodable {
    let id: String
    let note: String
    let input: DayInput
    let output: DayOutput
}

private func dayCase(
    _ ctx: GoldenContext,
    id: String,
    note: String,
    settings spec: SettingsSpec = SettingsSpec(),
    sessions: [SessionSpec]
) -> DayCase {
    let settings = spec.build(calendar: ctx.calendar)
    let built = sessions.map { $0.build() }

    let dayAware = OvertimeCalculator.dayAwareBreakdowns(sessions: built, settings: settings, calendar: ctx.calendar)
        .map { DayRow(sessionId: $0.session.id.uuidString.lowercased(), breakdown: BreakdownOut($0.breakdown)) }
    let perSession = built.map {
        DayRow(
            sessionId: $0.id.uuidString.lowercased(),
            breakdown: BreakdownOut(OvertimeCalculator.breakdown(for: $0, in: built, settings: settings, calendar: ctx.calendar))
        )
    }
    let streaks = OvertimeCalculator.sickStreakDayNumbers(sessions: built, calendar: ctx.calendar)
        .sorted { $0.key < $1.key }
        .map { StreakRow(day: ctx.localDay($0.key), number: $0.value) }

    return DayCase(
        id: id,
        note: note,
        input: DayInput(settings: spec, sessions: sessions),
        output: DayOutput(dayAware: dayAware, perSession: perSession, sickStreaks: streaks)
    )
}

/// The end of a shift of `hours` starting at `start`; the length is rounded to whole seconds.
private func end(_ start: Date, hours: Double) -> Date {
    start.addingTimeInterval((hours * 3600).rounded())
}

// MARK: - breakdown_day: several shifts in one day, breaks, night shifts, odd inputs

func writeBreakdownDay(_ ctx: GoldenContext) throws {
    // 2026-06-10 is a Wednesday and not near a daylight-saving change.
    func t(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date { ctx.local(2026, 6, day, hour, minute) }
    var cases: [DayCase] = []

    cases.append(dayCase(ctx, id: "two-shifts-fill-the-standard-day-exactly",
                         note: "4h + 4h36 = 8.6h, exactly the standard day",
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 12)),
                                    ctx.session(2, from: t(10, 13), to: t(10, 17, 36))]))
    cases.append(dayCase(ctx, id: "two-shifts-second-enters-overtime",
                         note: "5h + 5h: the second shift crosses into 125%",
                         sessions: [ctx.session(1, from: t(10, 7), to: t(10, 12)),
                                    ctx.session(2, from: t(10, 13), to: t(10, 18))]))
    cases.append(dayCase(ctx, id: "three-shifts",
                         note: "3h + 4h + 5h = 12h across both overtime tiers",
                         sessions: [ctx.session(1, from: t(10, 6), to: t(10, 9)),
                                    ctx.session(2, from: t(10, 10), to: t(10, 14)),
                                    ctx.session(3, from: t(10, 15), to: t(10, 20))]))
    cases.append(dayCase(ctx, id: "shifts-given-out-of-order",
                         note: "later shift listed first: processing is by clock-in order",
                         sessions: [ctx.session(2, from: t(10, 13), to: t(10, 18)),
                                    ctx.session(1, from: t(10, 7), to: t(10, 12))]))
    cases.append(dayCase(ctx, id: "first-shift-has-zero-paid-hours",
                         note: "1h shift with a 60 minute break: the daily allowance goes to the next shift",
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 9), breakMinutes: 60),
                                    ctx.session(2, from: t(10, 10), to: t(10, 14))]))
    cases.append(dayCase(ctx, id: "unpaid-break-30",
                         note: "9h shift with a 30 minute break",
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 17), breakMinutes: 30)]))
    cases.append(dayCase(ctx, id: "unpaid-break-45",
                         note: "9h shift with a 45 minute break",
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 17), breakMinutes: 45)]))
    cases.append(dayCase(ctx, id: "regular-then-night",
                         note: "8h day shift, then a night shift: each shift uses its own standard day (8.6 / 7) but they share one hours counter",
                         sessions: [ctx.session(1, from: t(10, 6), to: t(10, 14)),
                                    ctx.session(2, from: t(10, 22), to: t(11, 6), night: true)]))
    cases.append(dayCase(ctx, id: "night-then-regular",
                         note: "night shift first, then a day shift on the same date",
                         sessions: [ctx.session(1, from: t(10, 0), to: t(10, 6), night: true),
                                    ctx.session(2, from: t(10, 8), to: t(10, 17))]))
    cases.append(dayCase(ctx, id: "night-only-9h",
                         note: "9h night shift: overtime starts after 7h",
                         sessions: [ctx.session(1, from: t(10, 22), to: t(11, 7), night: true)]))
    cases.append(dayCase(ctx, id: "rest-day-two-shifts",
                         note: "rest day: 150% from the first hour",
                         sessions: [ctx.session(1, from: t(13, 8), to: t(13, 16), dayType: .restDay),
                                    ctx.session(2, from: t(13, 18), to: t(13, 20), dayType: .restDay)]))
    cases.append(dayCase(ctx, id: "holiday-single-shift",
                         note: "holiday, 11h",
                         sessions: [ctx.session(1, from: t(10, 7), to: t(10, 18), dayType: .holiday)]))
    cases.append(dayCase(ctx, id: "regular-and-holiday-same-day",
                         note: "two shifts with different day types on one date",
                         sessions: [ctx.session(1, from: t(10, 6), to: t(10, 10)),
                                    ctx.session(2, from: t(10, 12), to: t(10, 20), dayType: .holiday)]))

    var zeroRate = SettingsSpec()
    zeroRate.hourlyRate = 0
    zeroRate.dailyGasAllowance = 0
    cases.append(dayCase(ctx, id: "zero-gross-day", note: "no rate and no allowance: net shares are zero", settings: zeroRate,
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 16))]))
    var gasOnly = SettingsSpec()
    gasOnly.hourlyRate = 0
    cases.append(dayCase(ctx, id: "allowance-only-day", note: "rate 0 but a travel allowance", settings: gasOnly,
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 16))]))

    cases.append(dayCase(ctx, id: "open-shift-next-to-closed",
                         note: "an open shift has no clock-out, so it has zero hours",
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 16)),
                                    ctx.session(2, from: t(10, 18), to: nil)]))
    cases.append(dayCase(ctx, id: "clock-out-before-clock-in",
                         note: "malformed shift: hours are clamped at zero",
                         sessions: [ctx.session(1, from: t(10, 16), to: t(10, 8))]))

    cases.append(dayCase(ctx, id: "three-separate-days",
                         note: "days never share overtime allowance",
                         sessions: [ctx.session(1, from: t(8, 8), to: t(8, 20)),
                                    ctx.session(2, from: t(9, 8), to: t(9, 16)),
                                    ctx.session(3, from: t(10, 8), to: t(10, 22))]))

    var noCap = SettingsSpec()
    noCap.ot125HoursCap = 0
    cases.append(dayCase(ctx, id: "no-125-tier", note: "cap 0: everything beyond the standard day is 150%", settings: noCap,
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 20))]))
    var nineHourDay = SettingsSpec()
    nineHourDay.standardDayHours = 9
    nineHourDay.ot125HoursCap = 3
    cases.append(dayCase(ctx, id: "custom-standard-day", note: "9h standard day, 3h cap", settings: nineHourDay,
                         sessions: [ctx.session(1, from: t(10, 7), to: t(10, 21))]))
    var family = SettingsSpec()
    family.maritalStatus = "married"
    family.hasChildren = true
    family.numberOfChildren = 3
    cases.append(dayCase(ctx, id: "family-tax-profile", note: "married, three children", settings: family,
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 19))]))

    try ctx.write(group: "breakdown_day", cases: cases)
}

// MARK: - day_types: rest day, holiday, sick pay streaks

func writeDayTypes(_ ctx: GoldenContext) throws {
    func t(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date { ctx.local(2026, 6, day, hour, minute) }
    var cases: [DayCase] = []

    // Premium rates by day type and length.
    for dayType in [DayType.regular, .restDay, .holiday] {
        for hours in [5.0, 8.6, 10.0, 12.0, 14.0] {
            let start = t(10, 6)
            cases.append(dayCase(ctx, id: "\(dayType.rawValue)-\(hours)h",
                                 note: "single \(hours)h shift on a \(dayType.rawValue) day",
                                 sessions: [ctx.session(1, from: start, to: end(start, hours: hours), dayType: dayType)]))
        }
    }

    // A sick day carries no worked hours; its clock times are an expected shift.
    func sick(_ n: Int, day: Int, night: Bool = false) -> SessionSpec {
        ctx.session(n, from: t(day, 8), to: t(day, 16, 36), dayType: .sick, night: night)
    }

    cases.append(dayCase(ctx, id: "sick-day-1", note: "first day of sick leave: unpaid", sessions: [sick(1, day: 10)]))
    cases.append(dayCase(ctx, id: "sick-days-2", note: "second day: half pay", sessions: [sick(1, day: 10), sick(2, day: 11)]))
    cases.append(dayCase(ctx, id: "sick-days-3", note: "third day: half pay",
                         sessions: [sick(1, day: 10), sick(2, day: 11), sick(3, day: 12)]))
    cases.append(dayCase(ctx, id: "sick-days-4", note: "fourth day: full pay",
                         sessions: [sick(1, day: 8), sick(2, day: 9), sick(3, day: 10), sick(4, day: 11)]))
    cases.append(dayCase(ctx, id: "sick-days-5", note: "fifth day: full pay",
                         sessions: [sick(1, day: 7), sick(2, day: 8), sick(3, day: 9), sick(4, day: 10), sick(5, day: 11)]))
    cases.append(dayCase(ctx, id: "sick-streak-broken-by-a-gap", note: "Mon, Tue, then Thu: Thursday starts a new streak",
                         sessions: [sick(1, day: 8), sick(2, day: 9), sick(3, day: 11)]))
    cases.append(dayCase(ctx, id: "sick-streak-through-the-weekend",
                         note: "Thu, Fri, Sat, Sun all marked sick: calendar-adjacent days continue the streak",
                         sessions: [sick(1, day: 11), sick(2, day: 12), sick(3, day: 13), sick(4, day: 14)]))
    cases.append(dayCase(ctx, id: "sick-streak-broken-by-unmarked-rest-day",
                         note: "Thu, Fri, then Sun with Saturday (the rest day) not marked: the streak restarts",
                         sessions: [sick(1, day: 11), sick(2, day: 12), sick(3, day: 14)]))
    cases.append(dayCase(ctx, id: "sick-on-the-weekly-rest-day",
                         note: "a sick day that falls on Saturday is priced as sick, not as a rest day",
                         sessions: [sick(1, day: 12), sick(2, day: 13)]))
    cases.append(dayCase(ctx, id: "restday-session-on-a-weekday",
                         note: "the day type on the session decides, not the weekday",
                         sessions: [ctx.session(1, from: t(10, 8), to: t(10, 16), dayType: .restDay)]))

    // Combinations on one date (the day type of each session is used independently).
    cases.append(dayCase(ctx, id: "rest-day-then-sick-same-day",
                         note: "a rest-day shift and a sick entry on the same date",
                         sessions: [ctx.session(1, from: t(13, 18), to: t(13, 22), dayType: .restDay), sick(2, day: 13)]))
    cases.append(dayCase(ctx, id: "sick-then-rest-day-same-day",
                         note: "the same two entries with the rest-day shift earlier",
                         sessions: [ctx.session(1, from: t(13, 6), to: t(13, 10), dayType: .restDay),
                                    ctx.session(2, from: t(13, 12), to: t(13, 20, 36), dayType: .sick)]))
    cases.append(dayCase(ctx, id: "holiday-and-sick-same-day",
                         note: "a holiday shift and a sick entry on the same date",
                         sessions: [ctx.session(1, from: t(10, 6), to: t(10, 12), dayType: .holiday), sick(2, day: 10)]))
    cases.append(dayCase(ctx, id: "sick-then-regular-same-day",
                         note: "sick entry and a worked shift on one date: gas goes to the worked shift",
                         sessions: [sick(1, day: 10), ctx.session(2, from: t(10, 17), to: t(10, 21))]))

    cases.append(dayCase(ctx, id: "sick-night-shift-standard-day",
                         note: "a sick day flagged as night uses the 7h standard day",
                         sessions: [sick(1, day: 10), sick(2, day: 11, night: true), sick(3, day: 12, night: true), sick(4, day: 13, night: true)]))
    var zeroRate = SettingsSpec()
    zeroRate.hourlyRate = 0
    cases.append(dayCase(ctx, id: "sick-with-zero-rate", note: "rate 0", settings: zeroRate,
                         sessions: [sick(1, day: 10), sick(2, day: 11), sick(3, day: 12), sick(4, day: 13)]))
    var customDay = SettingsSpec()
    customDay.standardDayHours = 8
    customDay.hourlyRate = 61.35
    cases.append(dayCase(ctx, id: "sick-custom-standard-day", note: "8h standard day, odd rate", settings: customDay,
                         sessions: [sick(1, day: 8), sick(2, day: 9), sick(3, day: 10), sick(4, day: 11)]))

    try ctx.write(group: "day_types", cases: cases)
}
