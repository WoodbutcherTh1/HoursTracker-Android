import Foundation

// Groups: tax_monthly, tax_daily, tax_credit, settings_normalization.

/// The tax profiles used by the monthly and daily groups.
private let taxProfiles: [(id: String, spec: SettingsSpec)] = {
    var single = SettingsSpec()
    single.maritalStatus = "single"

    var marriedNotEmployed = SettingsSpec()
    marriedNotEmployed.maritalStatus = "married"
    marriedNotEmployed.spouseEmployed = false

    var marriedEmployed = SettingsSpec()
    marriedEmployed.maritalStatus = "married"
    marriedEmployed.spouseEmployed = true

    var threeChildren = SettingsSpec()
    threeChildren.hasChildren = true
    threeChildren.numberOfChildren = 3

    // Born far from the 67th birthday, because the age is computed from today's date.
    var retired = SettingsSpec()
    retired.birthDate = "1940-01-01"

    return [
        ("single", single),
        ("married-spouse-not-employed", marriedNotEmployed),
        ("married-spouse-employed", marriedEmployed),
        ("single-three-children", threeChildren),
        ("retired-born-1940", retired)
    ]
}()

// MARK: - tax_monthly: IsraeliTaxEstimator.estimateMonthlyDeductions

private struct MonthlyInput: Encodable {
    let monthlyGross: Double
    let settings: SettingsSpec
}

private struct MonthlyOutput: Encodable {
    let incomeTax: GoldenDouble
    let nationalInsurance: GoldenDouble
    let healthTax: GoldenDouble
    let creditOffset: GoldenDouble
    let total: GoldenDouble
    let retired: Bool
}

private struct MonthlyCase: Encodable {
    let id: String
    let input: MonthlyInput
    let output: MonthlyOutput
}

func writeTaxMonthly(_ ctx: GoldenContext) throws {
    // Every bracket limit and the National Insurance threshold, and a cent either side.
    let gross: [Double] = [
        -100, 0, 1, 7010, 7010.01, 7521.99, 7522, 7522.01, 10060, 16150, 22440, 46690, 60130, 60130.01, 100000, 250000
    ]
    var cases: [MonthlyCase] = []
    for profile in taxProfiles {
        let settings = profile.spec.build(calendar: ctx.calendar)
        for value in gross {
            let result = IsraeliTaxEstimator.estimateMonthlyDeductions(monthlyGross: value, settings: settings)
            cases.append(MonthlyCase(
                id: "\(profile.id)-gross\(value)",
                input: MonthlyInput(monthlyGross: value, settings: profile.spec),
                output: MonthlyOutput(
                    incomeTax: GoldenDouble(result.incomeTax),
                    nationalInsurance: GoldenDouble(result.nationalInsurance),
                    healthTax: GoldenDouble(result.healthTax),
                    creditOffset: GoldenDouble(result.creditOffset),
                    total: GoldenDouble(result.total),
                    retired: settings.hasReachedRetirementAge
                )
            ))
        }
    }
    try ctx.write(group: "tax_monthly", cases: cases)
}

// MARK: - tax_daily: IsraeliTaxEstimator.estimateDailyNet

private struct DailyInput: Encodable {
    let dailyGross: Double
    let settings: SettingsSpec
}

private struct DailyOutput: Encodable {
    let net: GoldenDouble
    let incomeTax: GoldenDouble
    let nationalInsurance: GoldenDouble
    let healthTax: GoldenDouble
    let creditApplied: GoldenDouble
}

private struct DailyCase: Encodable {
    let id: String
    let input: DailyInput
    let output: DailyOutput
}

func writeTaxDaily(_ ctx: GoldenContext) throws {
    let gross: [Double] = [0, 100, 347.2, 500, 835, 1500, 2765.5, 10000]
    var cases: [DailyCase] = []
    for profile in taxProfiles {
        let settings = profile.spec.build(calendar: ctx.calendar)
        for value in gross {
            let result = IsraeliTaxEstimator.estimateDailyNet(fromDailyGross: value, settings: settings)
            cases.append(DailyCase(
                id: "\(profile.id)-daily\(value)",
                input: DailyInput(dailyGross: value, settings: profile.spec),
                output: DailyOutput(
                    net: GoldenDouble(result.net),
                    incomeTax: GoldenDouble(result.incomeTax),
                    nationalInsurance: GoldenDouble(result.nationalInsurance),
                    healthTax: GoldenDouble(result.healthTax),
                    creditApplied: GoldenDouble(result.creditApplied)
                )
            ))
        }
    }
    try ctx.write(group: "tax_daily", cases: cases)
}

// MARK: - tax_credit: TaxCreditPointsCalculator

private struct CreditOutput: Encodable {
    /// After the settings initializer's clamping (0...15).
    let numberOfChildren: Int
    let creditPoints: GoldenDouble
    let monthlyCreditValue: GoldenDouble
    let dailyCreditValue: GoldenDouble
}

private struct CreditCase: Encodable {
    let id: String
    let input: SettingsSpec
    let output: CreditOutput
}

func writeTaxCredit(_ ctx: GoldenContext) throws {
    var cases: [CreditCase] = []

    func add(_ id: String, _ spec: SettingsSpec) {
        let settings = spec.build(calendar: ctx.calendar)
        cases.append(CreditCase(
            id: id,
            input: spec,
            output: CreditOutput(
                numberOfChildren: settings.numberOfChildren,
                creditPoints: GoldenDouble(TaxCreditPointsCalculator.creditPoints(for: settings)),
                monthlyCreditValue: GoldenDouble(TaxCreditPointsCalculator.monthlyCreditValue(for: settings)),
                dailyCreditValue: GoldenDouble(TaxCreditPointsCalculator.dailyCreditValue(for: settings))
            )
        ))
    }

    for marital in ["single", "married"] {
        for spouseEmployed in [false, true] {
            for hasChildren in [false, true] {
                for children in [0, 1, 3, 15] {
                    var spec = SettingsSpec()
                    spec.maritalStatus = marital
                    spec.spouseEmployed = spouseEmployed
                    spec.hasChildren = hasChildren
                    spec.numberOfChildren = children
                    add("\(marital)-spouseEmployed\(spouseEmployed)-hasChildren\(hasChildren)-children\(children)", spec)
                }
            }
        }
    }

    // Out-of-range child counts are clamped by the settings initializer before any points are counted.
    for children in [20, -3] {
        var spec = SettingsSpec()
        spec.hasChildren = true
        spec.numberOfChildren = children
        add("children-out-of-range-\(children)", spec)
    }

    try ctx.write(group: "tax_credit", cases: cases)
}

// MARK: - settings_normalization: clamping in the initializer and defaults when decoding JSON

/// Every setting that is clamped or defaulted, as the settings object ends up holding it.
private struct NormalizedOut: Encodable {
    let hourlyRate: GoldenDouble
    let dailyGasAllowance: GoldenDouble
    let standardDayHours: GoldenDouble
    let ot125HoursCap: GoldenDouble
    let nightStandardDayHours: GoldenDouble
    let weeklyStandardHours: GoldenDouble
    let weeklyOvertimeCapHours: GoldenDouble
    let locationRadiusMeters: GoldenDouble
    let numberOfChildren: Int
    let payrollStartDay: Int
    let restDayWeekday: Int
    let secondRestDayWeekday: Int?
    let defaultBreakMinutes: Int
    let expectedShiftStartHour: Int
    let expectedShiftStartMinute: Int
    let maritalStatus: String
    let hasChildren: Bool
    let spouseEmployed: Bool
    let breaksArePaid: Bool
    let currencyCode: String

    init(_ s: WorkplaceSettings) {
        hourlyRate = GoldenDouble(s.hourlyRate)
        dailyGasAllowance = GoldenDouble(s.dailyGasAllowance)
        standardDayHours = GoldenDouble(s.standardDayHours)
        ot125HoursCap = GoldenDouble(s.ot125HoursCap)
        nightStandardDayHours = GoldenDouble(s.nightStandardDayHours)
        weeklyStandardHours = GoldenDouble(s.weeklyStandardHours)
        weeklyOvertimeCapHours = GoldenDouble(s.weeklyOvertimeCapHours)
        locationRadiusMeters = GoldenDouble(s.locationRadiusMeters)
        numberOfChildren = s.numberOfChildren
        payrollStartDay = s.payrollStartDay
        restDayWeekday = s.restDayWeekday
        secondRestDayWeekday = s.secondRestDayWeekday
        defaultBreakMinutes = s.defaultBreakMinutes
        expectedShiftStartHour = s.expectedShiftStartHour
        expectedShiftStartMinute = s.expectedShiftStartMinute
        maritalStatus = s.maritalStatus.rawValue
        hasChildren = s.hasChildren
        spouseEmployed = s.spouseEmployed
        breaksArePaid = s.breaksArePaid
        currencyCode = s.currencyCode
    }
}

private struct NormalizationCase: Encodable {
    let id: String
    let note: String
    /// Exactly one of `spec` (built through the initializer) or `json` (decoded) is set.
    let spec: SettingsSpec?
    let json: String?
    /// `false` when the decoder throws; then `settings` is absent.
    let decoded: Bool
    let settings: NormalizedOut?
}

func writeSettingsNormalization(_ ctx: GoldenContext) throws {
    var cases: [NormalizationCase] = []

    func fromSpec(_ id: String, _ note: String, _ change: (inout SettingsSpec) -> Void) {
        var spec = SettingsSpec()
        change(&spec)
        cases.append(NormalizationCase(
            id: id, note: note, spec: spec, json: nil,
            decoded: true, settings: NormalizedOut(spec.build(calendar: ctx.calendar))
        ))
    }

    func fromJSON(_ id: String, _ note: String, _ json: String) {
        let decoded = try? JSONDecoder().decode(WorkplaceSettings.self, from: Data(json.utf8))
        cases.append(NormalizationCase(
            id: id, note: note, spec: nil, json: json,
            decoded: decoded != nil, settings: decoded.map(NormalizedOut.init)
        ))
    }

    // Through the initializer (clamping).
    fromSpec("in-range", "nothing changes") { _ in }
    fromSpec("negative-money", "rate and allowance cannot be negative") { $0.hourlyRate = -5; $0.dailyGasAllowance = -1 }
    fromSpec("standard-day-zero", "standard day is at least 0.1") { $0.standardDayHours = 0 }
    fromSpec("standard-day-30", "standard day is at most 24") { $0.standardDayHours = 30 }
    fromSpec("standard-day-24", "24 is allowed") { $0.standardDayHours = 24 }
    fromSpec("overtime-cap-negative", "cap cannot be negative") { $0.ot125HoursCap = -1 }
    fromSpec("children-20", "at most 15 children") { $0.numberOfChildren = 20 }
    fromSpec("children-negative", "at least 0 children") { $0.numberOfChildren = -3 }
    fromSpec("payroll-day-0", "payroll day is at least 1") { $0.payrollStartDay = 0 }
    fromSpec("payroll-day-31", "payroll day is at most 28") { $0.payrollStartDay = 31 }
    fromSpec("payroll-day-15", "15 is allowed") { $0.payrollStartDay = 15 }
    fromSpec("rest-day-0", "weekday is at least 1") { $0.restDayWeekday = 0 }
    fromSpec("rest-day-8", "weekday is at most 7") { $0.restDayWeekday = 8 }
    fromSpec("second-rest-day-equals-first", "a second rest day equal to the first is dropped") { $0.restDayWeekday = 6; $0.secondRestDayWeekday = 6 }
    fromSpec("second-rest-day-clamps-onto-first", "9 clamps to 7, which equals the first, so it is dropped") { $0.secondRestDayWeekday = 9 }
    fromSpec("second-rest-day-0", "0 clamps to 1") { $0.secondRestDayWeekday = 0 }
    fromSpec("second-rest-day-valid", "Friday and Saturday") { $0.restDayWeekday = 7; $0.secondRestDayWeekday = 6 }
    fromSpec("default-break-negative", "break minutes cannot be negative") { $0.defaultBreakMinutes = -10 }
    fromSpec("night-day-zero", "night standard day is at least 0.1") { $0.nightStandardDayHours = 0 }
    fromSpec("night-day-25", "night standard day is at most 24") { $0.nightStandardDayHours = 25 }
    fromSpec("weekly-standard-0", "weekly standard is at least 1") { $0.weeklyStandardHours = 0 }
    fromSpec("weekly-standard-100", "weekly standard is at most 60") { $0.weeklyStandardHours = 100 }
    fromSpec("weekly-cap-negative", "weekly cap cannot be negative") { $0.weeklyOvertimeCapHours = -1 }
    fromSpec("weekly-cap-30", "weekly cap is at most 24") { $0.weeklyOvertimeCapHours = 30 }
    fromSpec("shift-start-hour-25", "hour is at most 23") { $0.expectedShiftStartHour = 25 }
    fromSpec("shift-start-minute-negative", "minute is at least 0") { $0.expectedShiftStartMinute = -1 }
    fromSpec("shift-start-minute-75", "minute is at most 59") { $0.expectedShiftStartMinute = 75 }

    // Decoded from JSON (what a restored backup does): defaults for absent keys, then clamping.
    let required = """
    "workplaceName":"W","workerFullName":"N","workerIDNumber":"","employeeNumber":"",\
    "hourlyRate":50,"dailyGasAllowance":20,"standardDayHours":8.6,"ot125HoursCap":2,"locationRadiusMeters":150
    """
    fromJSON("json-minimal", "only the required keys: everything else takes its default", "{\(required)}")
    fromJSON("json-unknown-keys-ignored", "keys the app does not know are ignored", "{\(required),\"somethingNew\":123}")
    fromJSON("json-out-of-range", "values are clamped after decoding",
             "{\(required),\"numberOfChildren\":99,\"payrollStartDay\":40,\"restDayWeekday\":0,\"weeklyStandardHours\":500,\"weeklyOvertimeCapHours\":-4,\"defaultBreakMinutes\":-1}")
    fromJSON("json-radius-clamped", "location radius is clamped to 50...2000",
             "{\"workplaceName\":\"W\",\"workerFullName\":\"N\",\"workerIDNumber\":\"\",\"employeeNumber\":\"\",\"hourlyRate\":50,\"dailyGasAllowance\":20,\"standardDayHours\":8.6,\"ot125HoursCap\":2,\"locationRadiusMeters\":10}")
    fromJSON("json-second-rest-day-null", "an explicit null second rest day", "{\(required),\"restDayWeekday\":6,\"secondRestDayWeekday\":null}")
    fromJSON("json-second-rest-day-equal", "a second rest day equal to the first is dropped", "{\(required),\"restDayWeekday\":6,\"secondRestDayWeekday\":6}")
    fromJSON("json-married", "married with children", "{\(required),\"maritalStatus\":\"married\",\"hasChildren\":true,\"numberOfChildren\":2,\"spouseEmployed\":true}")
    fromJSON("json-currency-and-breaks", "currency and paid breaks", "{\(required),\"currencyCode\":\"USD\",\"breaksArePaid\":true,\"defaultBreakMinutes\":30}")
    fromJSON("json-missing-required-key", "no hourlyRate: decoding fails",
             "{\"workplaceName\":\"W\",\"workerFullName\":\"N\",\"workerIDNumber\":\"\",\"employeeNumber\":\"\",\"dailyGasAllowance\":20,\"standardDayHours\":8.6,\"ot125HoursCap\":2,\"locationRadiusMeters\":150}")
    fromJSON("json-wrong-type", "a number where text is expected: decoding fails", "{\(required),\"numberOfChildren\":\"three\"}")
    fromJSON("json-unknown-marital-status", "an unknown enum value: decoding fails", "{\(required),\"maritalStatus\":\"divorced\"}")

    try ctx.write(group: "settings_normalization", cases: cases)
}
