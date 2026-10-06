import Foundation

/// A pinned environment the harness runs in. Some iOS functions use `Calendar.current`
/// internally, so the process itself must be pinned, not only the calendars we pass in.
struct GoldenEnvironment {
    let id: String
    let timeZoneIdentifier: String
    let localeIdentifier: String
    /// 1 = Sunday ... 7 = Saturday, as `Calendar.firstWeekday`.
    let expectedFirstWeekday: Int

    static let il = GoldenEnvironment(id: "il", timeZoneIdentifier: "Asia/Jerusalem", localeIdentifier: "he_IL", expectedFirstWeekday: 1)
    static let ru = GoldenEnvironment(id: "ru", timeZoneIdentifier: "Europe/Moscow", localeIdentifier: "ru_RU", expectedFirstWeekday: 2)
    static let utc = GoldenEnvironment(id: "utc", timeZoneIdentifier: "UTC", localeIdentifier: "en_GB", expectedFirstWeekday: 2)

    static let all = [il, ru, utc]

    static var platform: String {
        #if os(macOS)
        return "darwin"
        #else
        return "linux"
        #endif
    }
}

/// What the environment really looks like once pinned. Written into every data file so the
/// Kotlin tests use the values iOS actually used instead of assuming them.
struct EnvironmentInfo: Encodable {
    let id: String
    let timeZone: String
    let requestedLocale: String
    let systemLocale: String
    let firstWeekday: Int
    let minimumDaysInFirstWeek: Int
    let platform: String
}

/// Everything a case group needs: the pinned calendar and a place to write its file.
final class GoldenContext {
    let environment: GoldenEnvironment
    let info: EnvironmentInfo
    /// Equal to `Calendar.current` (verified at startup), passed explicitly wherever iOS accepts one.
    let calendar: Calendar
    let outDirectory: URL
    private(set) var writtenFiles: [String] = []

    fileprivate init(environment: GoldenEnvironment, info: EnvironmentInfo, calendar: Calendar, outDirectory: URL) {
        self.environment = environment
        self.info = info
        self.calendar = calendar
        self.outDirectory = outDirectory
    }

    /// Writes `<group>.<environment>.json`, one case per line so diffs stay readable.
    func write<Case: Encodable>(group: String, cases: [Case]) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]

        func text<T: Encodable>(_ value: T) throws -> String {
            String(decoding: try encoder.encode(value), as: UTF8.self)
        }

        var output = "{\n"
        output += "\"formatVersion\": 1,\n"
        output += "\"group\": \"\(group)\",\n"
        output += "\"environment\": \(try text(info)),\n"
        output += "\"cases\": [\n"
        output += try cases.map { "  " + (try text($0)) }.joined(separator: ",\n")
        output += "\n]\n}\n"

        let name = "\(group).\(environment.id).json"
        try output.write(to: outDirectory.appendingPathComponent(name), atomically: true, encoding: .utf8)
        writtenFiles.append(name)
        print("  wrote \(name) (\(cases.count) cases)")
    }
}

extension GoldenEnvironment {
    /// Pins the default time zone, then checks that `Calendar.current` really matches.
    /// Stops the process with an explanation when it does not: data produced in an
    /// unpinned environment would silently depend on the machine it was generated on.
    ///
    /// `lenient` (used only outside macOS, where the locale cannot be pinned) keeps the time
    /// zone check strict but only warns about the first weekday; the real values are still
    /// recorded in the file, so the data stays consistent with itself.
    func pinAndVerify(outDirectory: URL, lenient: Bool = false) -> GoldenContext {
        guard let zone = TimeZone(identifier: timeZoneIdentifier) else {
            fail("Unknown time zone \(timeZoneIdentifier).", code: 70)
        }
        NSTimeZone.default = zone

        let current = Calendar.current
        var problems: [String] = []
        if current.identifier != .gregorian {
            problems.append("calendar is \(current.identifier), expected gregorian")
        }
        if !Self.sameZone(current.timeZone, zone) {
            problems.append("time zone is \(current.timeZone.identifier), expected \(timeZoneIdentifier)")
        }
        if current.firstWeekday != expectedFirstWeekday {
            let message = "first weekday is \(current.firstWeekday), expected \(expectedFirstWeekday)"
            if lenient {
                print("  WARNING (lenient): \(message); recording the real value.")
            } else {
                problems.append(message)
            }
        }
        if !problems.isEmpty {
            fail(
                """
                Could not pin the environment '\(id)': \(problems.joined(separator: "; ")).
                System locale in effect: \(Locale.current.identifier).
                The harness refuses to write data that would depend on this machine's settings.
                Run it through golden/run.sh, which passes the locale to the process.
                """,
                code: 70
            )
        }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        calendar.firstWeekday = current.firstWeekday
        calendar.minimumDaysInFirstWeek = current.minimumDaysInFirstWeek
        calendar.locale = Locale(identifier: localeIdentifier)

        let info = EnvironmentInfo(
            id: id,
            timeZone: timeZoneIdentifier,
            requestedLocale: localeIdentifier,
            systemLocale: Locale.current.identifier,
            firstWeekday: current.firstWeekday,
            minimumDaysInFirstWeek: current.minimumDaysInFirstWeek,
            platform: Self.platform
        )
        return GoldenContext(environment: self, info: info, calendar: calendar, outDirectory: outDirectory)
    }

    private static func sameZone(_ a: TimeZone, _ b: TimeZone) -> Bool {
        if a.identifier == b.identifier { return true }
        // "UTC" and "GMT" are the same zone for our purposes.
        return a.secondsFromGMT() == 0 && b.secondsFromGMT() == 0
            && ["UTC", "GMT"].contains(a.identifier) && ["UTC", "GMT"].contains(b.identifier)
    }
}
