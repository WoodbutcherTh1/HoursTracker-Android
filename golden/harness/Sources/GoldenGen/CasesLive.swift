import Foundation

// Groups: live_pay_curve, live_pay_curve_eval, money_locale, money_env.

// MARK: - live_pay_curve: AppViewModel.makeLivePayCurve and LivePayCurve.pay(at:)

private struct CurvePointOut: Encodable {
    let paidSeconds: GoldenDouble
    let gross: GoldenDouble
    let net: GoldenDouble
}

private struct ProbeOut: Encodable {
    /// Seconds after `now` at which the curve was read.
    let offsetSeconds: Int
    let gross: GoldenDouble
    let net: GoldenDouble
}

private struct CurveOut: Encodable {
    let paidClockStartEpoch: GoldenDouble
    /// Present only while an unpaid break is running.
    let pausedPaidSeconds: GoldenDouble?
    let currencyCode: String
    let points: [CurvePointOut]
    let probes: [ProbeOut]
}

private struct CurveInput: Encodable {
    let settings: SettingsSpec
    /// The running shift.
    let openSession: SessionSpec
    /// Other shifts of the same workplace (earlier shifts on the same day share the allowance).
    let otherSessions: [SessionSpec]
    let now: String
}

private struct CurveCase: Encodable {
    let id: String
    let note: String
    let input: CurveInput
    let output: CurveOut
}

private let probeOffsets: [Int] = [-3600, 0, 1800, 5873, 8 * 3600, 16 * 3600, 20 * 3600]

func writeLivePayCurve(_ ctx: GoldenContext) throws {
    func t(_ hour: Int, _ minute: Int = 0) -> Date { ctx.local(2026, 6, 10, hour, minute) }
    var cases: [CurveCase] = []

    func add(
        _ id: String, _ note: String,
        settings spec: SettingsSpec = SettingsSpec(),
        open openSpec: SessionSpec,
        others: [SessionSpec] = [],
        now: Date
    ) {
        let settings = spec.build(calendar: ctx.calendar)
        let open = openSpec.build()
        let viewModel = AppViewModel(settings: settings, sessions: others.map { $0.build() } + [open])
        let curve = viewModel.makeLivePayCurve(for: open, now: now)

        let probes = probeOffsets.map { offset -> ProbeOut in
            let pay = curve.pay(at: now.addingTimeInterval(TimeInterval(offset)))
            return ProbeOut(offsetSeconds: offset, gross: GoldenDouble(pay.gross), net: GoldenDouble(pay.net))
        }
        cases.append(CurveCase(
            id: id, note: note,
            input: CurveInput(settings: spec, openSession: openSpec, otherSessions: others, now: ISO.string(now)),
            output: CurveOut(
                paidClockStartEpoch: GoldenDouble(curve.paidClockStart.timeIntervalSince1970),
                pausedPaidSeconds: curve.pausedPaidSeconds.map(GoldenDouble.init),
                currencyCode: curve.currencyCode,
                points: curve.points.map { CurvePointOut(paidSeconds: GoldenDouble($0.paidSeconds), gross: GoldenDouble($0.gross), net: GoldenDouble($0.net)) },
                probes: probes
            )
        ))
    }

    /// `breakMinutes` must agree with the finished breaks, as the app keeps it: ending a live break
    /// folds its length into `breakMinutes` right away (see `WorkSession.endBreak`).
    func running(_ n: Int = 1, from start: Date, dayType: DayType = .regular, night: Bool = false,
                 breaks: [BreakSpec] = [], breakMinutes: Int = 0) -> SessionSpec {
        ctx.session(n, from: start, to: nil, breakMinutes: breakMinutes, breaks: breaks, dayType: dayType, night: night)
    }

    var withDefaultBreak = SettingsSpec()
    withDefaultBreak.defaultBreakMinutes = 30

    add("just-clocked-in", "no time has passed", open: running(from: t(8)), now: t(8))
    add("one-hour-in", "one hour in", open: running(from: t(8)), now: t(9))
    add("before-default-break", "5h59: the 30 minute default break is not applied yet",
        settings: withDefaultBreak, open: running(from: t(8)), now: t(13, 59))
    add("at-default-break-threshold", "exactly 6h: the default break starts to apply, so pay steps down along the curve",
        settings: withDefaultBreak, open: running(from: t(8)), now: t(14))
    add("nine-hours-in", "9h: into overtime, with the default break", settings: withDefaultBreak, open: running(from: t(8)), now: t(17))

    let finished = BreakSpec(start: ISO.string(t(10)), end: ISO.string(t(10, 20)))
    let running12 = BreakSpec(start: ISO.string(t(12)), end: nil)
    add("after-a-finished-break", "a 20 minute break already taken (break minutes already folded in, as the app keeps them)",
        open: running(from: t(8), breaks: [finished], breakMinutes: 20), now: t(14))
    add("unpaid-break-running", "an unpaid break is running: the paid clock is stopped, so the curve is a single flat point",
        open: running(from: t(8), breaks: [finished, running12], breakMinutes: 20), now: t(12, 30))
    var paidBreaks = SettingsSpec()
    paidBreaks.breaksArePaid = true
    add("paid-break-running", "a workplace that pays for breaks: the clock keeps running through the break",
        settings: paidBreaks, open: running(from: t(8), breaks: [running12]), now: t(12, 30))
    add("rest-day", "rest day: premium rates from the first hour", open: running(from: t(8), dayType: .restDay), now: t(17))
    add("holiday", "holiday, 5h in", open: running(from: t(8), dayType: .holiday), now: t(13))
    add("night-shift", "a night shift that started at 22:00, now 02:00 the next day",
        open: running(from: t(22), night: true), now: ctx.local(2026, 6, 11, 2))
    add("earlier-shift-same-day", "a closed 5h shift earlier the same day shares the standard-day allowance",
        open: running(2, from: t(12)), others: [ctx.session(1, from: t(6), to: t(11))], now: t(18))
    var oddSettings = SettingsSpec()
    oddSettings.hourlyRate = 61.35
    oddSettings.standardDayHours = 8
    oddSettings.ot125HoursCap = 3
    oddSettings.dailyGasAllowance = 0
    add("odd-settings", "rate 61.35, 8h day, 3h cap, no allowance", settings: oddSettings, open: running(from: t(8)), now: t(19))
    var family = SettingsSpec()
    family.maritalStatus = "married"
    family.hasChildren = true
    family.numberOfChildren = 2
    add("family-tax-profile", "married, two children: net differs from gross", settings: family, open: running(from: t(8)), now: t(16))
    add("across-spring-change", "22:00 on the evening before the spring change, now 05:00 (6 real hours)",
        open: running(from: ctx.local(2026, 3, 26, 22), night: true), now: ctx.local(2026, 3, 27, 5))
    add("across-autumn-change", "22:00 on the evening before the autumn change, now 05:00 (8 real hours)",
        open: running(from: ctx.local(2026, 10, 24, 22), night: true), now: ctx.local(2026, 10, 25, 5))

    try ctx.write(group: "live_pay_curve", cases: cases)
}

// MARK: - live_pay_curve_eval: LivePayCurve.pay(at:) on hand-made curves

private struct EvalPoint: Codable {
    let paidSeconds: Double
    let gross: Double
    let net: Double
}

private struct EvalInput: Encodable {
    let paidClockStartEpoch: Double
    let pausedPaidSeconds: Double?
    let points: [EvalPoint]
    /// Read the curve at `paidClockStartEpoch + offset` for each offset.
    let offsets: [Int]
}

private struct EvalCase: Encodable {
    let id: String
    let note: String
    let input: EvalInput
    let output: [ProbeOut]
}

func writeLivePayCurveEval(_ ctx: GoldenContext) throws {
    var cases: [EvalCase] = []
    let start = 1_780_000_000.0

    func add(_ id: String, _ note: String, paused: Double? = nil, _ points: [EvalPoint], offsets: [Int]) {
        let curve = LivePayCurve(
            paidClockStart: Date(timeIntervalSince1970: start),
            pausedPaidSeconds: paused,
            points: points.map { LivePayCurve.Point(paidSeconds: $0.paidSeconds, gross: $0.gross, net: $0.net) },
            currencyCode: "ILS"
        )
        let output = offsets.map { offset -> ProbeOut in
            let pay = curve.pay(at: Date(timeIntervalSince1970: start + Double(offset)))
            return ProbeOut(offsetSeconds: offset, gross: GoldenDouble(pay.gross), net: GoldenDouble(pay.net))
        }
        cases.append(EvalCase(
            id: id, note: note,
            input: EvalInput(paidClockStartEpoch: start, pausedPaidSeconds: paused, points: points, offsets: offsets),
            output: output
        ))
    }
    func p(_ seconds: Double, _ gross: Double, _ net: Double) -> EvalPoint { EvalPoint(paidSeconds: seconds, gross: gross, net: net) }

    let probes = [-100, 0, 1, 150, 300, 450, 600, 900, 1200, 5000]
    add("single-point", "one point: always that value", [p(0, 35, 30)], offsets: probes)
    add("two-points-linear", "interpolation between two points and extension past the last",
        [p(0, 0, 0), p(600, 100, 80)], offsets: probes)
    add("three-slopes", "a slope change at the second point",
        [p(0, 35, 30), p(300, 85, 70), p(900, 235, 190)], offsets: probes)
    add("first-point-later", "the curve starts after the clock does (a shift already in progress)",
        [p(300, 50, 45), p(600, 100, 90)], offsets: probes)
    add("zero-span-segment", "two points with the same paid seconds",
        [p(0, 0, 0), p(300, 50, 40), p(300, 60, 48), p(600, 110, 88)], offsets: probes)
    add("step-down", "pay falls between samples (a default break applied at 6h)",
        [p(0, 0, 0), p(300, 100, 80), p(600, 60, 50), p(900, 110, 90)], offsets: probes)
    add("paused", "an unpaid break: the clock is stopped at 450s", paused: 450,
        [p(450, 77, 61)], offsets: probes)
    add("paused-with-points", "paused with more than one point: the paused seconds still decide", paused: 400,
        [p(0, 0, 0), p(300, 50, 40), p(600, 100, 80)], offsets: probes)
    add("fractional-numbers", "values that are not exactly representable in binary",
        [p(0, 0.1, 0.05), p(300.7, 12.345, 10.001), p(900.3, 99.99, 80.04)], offsets: probes)

    try ctx.write(group: "live_pay_curve_eval", cases: cases)
}

// MARK: - money_locale / money_env: PayFormatter

private struct MoneyCase: Encodable {
    let id: String
    let amount: Double
    let currency: String
    /// `nil` means the process locale (`money_env`).
    let locale: String?
    /// The exact text Apple's formatter produced.
    let text: String
    /// The same text as space-separated hexadecimal code points, so invisible direction marks
    /// and special spaces can be seen when reviewing.
    let codePoints: String
}

private func codePoints(_ text: String) -> String {
    text.unicodeScalars.map { String($0.value, radix: 16) }.joined(separator: " ")
}

func writeMoneyLocale(_ ctx: GoldenContext) throws {
    let amounts: [Double] = [0, 1234.5, 1234.565, 0.005, -42.1, 1_000_000.5, 0.004, 1.005, 2.675, 99_999.995]
    var cases: [MoneyCase] = []
    for locale in ["he_IL", "ar", "ru_RU", "en_US"] {
        for currency in PayFormatter.supportedCurrencyCodes {
            for amount in amounts {
                let text = PayFormatter.string(amount, currencyCode: currency, locale: Locale(identifier: locale))
                cases.append(MoneyCase(id: "\(locale)-\(currency)-\(amount)", amount: amount, currency: currency, locale: locale,
                                       text: text, codePoints: codePoints(text)))
            }
        }
    }
    try ctx.write(group: "money_locale", cases: cases)
}

private struct SymbolCase: Encodable {
    let currency: String
    let symbol: String
    let codePoints: String
}

private struct MoneyEnvOutput: Encodable {
    let formatted: [MoneyCase]
    let symbols: [SymbolCase]
}

private struct MoneyEnvCase: Encodable {
    let id: String
    let output: MoneyEnvOutput
}

func writeMoneyEnv(_ ctx: GoldenContext) throws {
    // With no locale given the formatter follows the process locale, which run.sh pins per environment.
    let amounts: [Double] = [0, 1234.5, -42.1, 1_000_000.5]
    let formatted = PayFormatter.supportedCurrencyCodes.flatMap { currency in
        amounts.map { amount -> MoneyCase in
            let text = PayFormatter.string(amount, currencyCode: currency)
            return MoneyCase(id: "\(currency)-\(amount)", amount: amount, currency: currency, locale: nil, text: text, codePoints: codePoints(text))
        }
    }
    let symbols = PayFormatter.supportedCurrencyCodes.map { code -> SymbolCase in
        let symbol = PayFormatter.symbol(for: code)
        return SymbolCase(currency: code, symbol: symbol, codePoints: codePoints(symbol))
    }
    try ctx.write(group: "money_env", cases: [MoneyEnvCase(id: ctx.environment.id, output: MoneyEnvOutput(formatted: formatted, symbols: symbols))])
}
