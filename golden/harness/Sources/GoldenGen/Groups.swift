import Foundation

/// One golden-file producer. `environments == nil` runs it in every pinned environment;
/// otherwise only in the listed ones (for groups whose result cannot depend on the
/// environment, so the same data is not written three times).
struct GoldenGroup {
    let name: String
    let environments: Set<String>?
    let run: (GoldenContext) throws -> Void

    init(_ name: String, environments: Set<String>? = nil, run: @escaping (GoldenContext) throws -> Void) {
        self.name = name
        self.environments = environments
        self.run = run
    }

    func applies(to environment: GoldenEnvironment) -> Bool {
        environments?.contains(environment.id) ?? true
    }
}

/// Every case group, in the order they are written.
let goldenGroups: [GoldenGroup] = [
    GoldenGroup("breakdown_simple", environments: ["il"], run: writeBreakdownSimple),
    GoldenGroup("breakdown_day", run: writeBreakdownDay),
    GoldenGroup("day_types", run: writeDayTypes)
]

/// The result of `OvertimeCalculator` for one session or day, with every number as raw bits.
struct BreakdownOut: Encodable {
    let regularHours: GoldenDouble
    let ot125Hours: GoldenDouble
    let ot150Hours: GoldenDouble
    let totalHours: GoldenDouble
    let gasAllowance: GoldenDouble
    let basePay: GoldenDouble
    let ot125Pay: GoldenDouble
    let ot150Pay: GoldenDouble
    let totalPay: GoldenDouble
    let netPay: GoldenDouble
    let incomeTax: GoldenDouble
    let nationalInsurance: GoldenDouble
    let healthTax: GoldenDouble
    let creditPointsApplied: GoldenDouble
    let creditPoints: GoldenDouble
    let currencyCode: String

    init(_ b: DayPayBreakdown) {
        regularHours = GoldenDouble(b.regularHours)
        ot125Hours = GoldenDouble(b.ot125Hours)
        ot150Hours = GoldenDouble(b.ot150Hours)
        totalHours = GoldenDouble(b.totalHours)
        gasAllowance = GoldenDouble(b.gasAllowance)
        basePay = GoldenDouble(b.basePay)
        ot125Pay = GoldenDouble(b.ot125Pay)
        ot150Pay = GoldenDouble(b.ot150Pay)
        totalPay = GoldenDouble(b.totalPay)
        netPay = GoldenDouble(b.netPay)
        incomeTax = GoldenDouble(b.incomeTax)
        nationalInsurance = GoldenDouble(b.nationalInsurance)
        healthTax = GoldenDouble(b.healthTax)
        creditPointsApplied = GoldenDouble(b.creditPointsApplied)
        creditPoints = GoldenDouble(b.creditPoints)
        currencyCode = b.currencyCode
    }
}
