import Foundation

// Plain, language-neutral descriptions of the inputs. The harness builds the real iOS types
// from them, and the Kotlin tests build their own types from the same JSON.
// Numbers are written as ordinary JSON numbers (Swift prints the shortest text that
// round-trips, so both languages read the identical Double).

struct SettingsSpec: Codable {
    var hourlyRate: Double = 100
    var dailyGasAllowance: Double = 35
    var standardDayHours: Double = 8.6
    var ot125HoursCap: Double = 2
    var nightStandardDayHours: Double = 7
    var weeklyStandardHours: Double = 42
    var weeklyOvertimeCapHours: Double = 12
    var breaksArePaid: Bool = false
    var defaultBreakMinutes: Int = 0
    var currencyCode: String = "ILS"
    var maritalStatus: String = "single"
    var hasChildren: Bool = false
    var numberOfChildren: Int = 0
    var spouseEmployed: Bool = false
    /// "yyyy-MM-dd", local to the environment's time zone.
    var birthDate: String?
    var payrollStartDay: Int = 1
    var restDayWeekday: Int = 7
    var secondRestDayWeekday: Int?
    var expectedShiftStartHour: Int = 8
    var expectedShiftStartMinute: Int = 0

    func build(calendar: Calendar) -> WorkplaceSettings {
        WorkplaceSettings(
            workplaceName: "Golden",
            contractorName: nil,
            workerFullName: "Golden",
            workerIDNumber: "",
            employeeNumber: "",
            hourlyRate: hourlyRate,
            dailyGasAllowance: dailyGasAllowance,
            standardDayHours: standardDayHours,
            ot125HoursCap: ot125HoursCap,
            locationLatitude: nil,
            locationLongitude: nil,
            locationRadiusMeters: 150,
            maritalStatus: MaritalStatus(rawValue: maritalStatus)!,
            hasChildren: hasChildren,
            numberOfChildren: numberOfChildren,
            spouseEmployed: spouseEmployed,
            birthDate: birthDate.map { Self.localDate($0, calendar: calendar) },
            payrollStartDay: payrollStartDay,
            restDayWeekday: restDayWeekday,
            secondRestDayWeekday: secondRestDayWeekday,
            defaultBreakMinutes: defaultBreakMinutes,
            breaksArePaid: breaksArePaid,
            nightStandardDayHours: nightStandardDayHours,
            weeklyStandardHours: weeklyStandardHours,
            weeklyOvertimeCapHours: weeklyOvertimeCapHours,
            currencyCode: currencyCode,
            arrivalRemindersEnabled: false,
            expectedShiftStartHour: expectedShiftStartHour,
            expectedShiftStartMinute: expectedShiftStartMinute,
            modifiedAt: Date(timeIntervalSince1970: 0)
        )
    }

    private static func localDate(_ text: String, calendar: Calendar) -> Date {
        let parts = text.split(separator: "-").compactMap { Int($0) }
        precondition(parts.count == 3, "bad date \(text)")
        let components = DateComponents(year: parts[0], month: parts[1], day: parts[2])
        return calendar.date(from: components)!
    }
}

struct BreakSpec: Codable {
    var start: String
    var end: String?
}

struct SessionSpec: Codable {
    /// Deterministic UUID text, so output rows can be matched to inputs.
    var id: String
    /// Instants in UTC, whole seconds (see `ISO`).
    var date: String
    var clockIn: String
    var clockOut: String?
    var breakMinutes: Int = 0
    var breaks: [BreakSpec] = []
    var dayType: String = "regular"
    var isNightShift: Bool = false

    func build() -> WorkSession {
        WorkSession(
            id: UUID(uuidString: id)!,
            date: ISO.date(date),
            clockIn: ISO.date(clockIn),
            clockOut: clockOut.map(ISO.date),
            breakMinutes: breakMinutes,
            breaks: breaks.map { BreakInterval(start: ISO.date($0.start), end: $0.end.map(ISO.date)) },
            dayType: DayType(rawValue: dayType)!,
            isNightShift: isNightShift,
            modifiedAt: Date(timeIntervalSince1970: 0)
        )
    }
}

extension GoldenContext {
    /// Builds an instant from a wall-clock time in the pinned zone. Cases must not use a
    /// wall-clock time that does not exist or occurs twice; those are tested explicitly.
    func local(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 0, _ minute: Int = 0, _ second: Int = 0) -> Date {
        let components = DateComponents(year: year, month: month, day: day, hour: hour, minute: minute, second: second)
        return calendar.date(from: components)!
    }

    /// A shift on one local day (clock-out may be on the next day), with the day's start as `date`.
    func session(
        _ n: Int,
        from clockIn: Date,
        to clockOut: Date?,
        breakMinutes: Int = 0,
        breaks: [BreakSpec] = [],
        dayType: DayType = .regular,
        night: Bool = false
    ) -> SessionSpec {
        SessionSpec(
            id: String(format: "00000000-0000-0000-0000-%012d", n),
            date: ISO.string(calendar.startOfDay(for: clockIn)),
            clockIn: ISO.string(clockIn),
            clockOut: clockOut.map { ISO.string($0) },
            breakMinutes: breakMinutes,
            breaks: breaks,
            dayType: dayType.rawValue,
            isNightShift: night
        )
    }

    /// "yyyy-MM-dd" of an instant in the pinned zone.
    func localDay(_ date: Date) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
}
