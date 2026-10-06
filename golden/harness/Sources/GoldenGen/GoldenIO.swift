import Foundation

/// A `Double` written with its raw IEEE-754 bits, so the Kotlin tests can demand bit-for-bit
/// equality, plus the readable value for people reviewing the file.
struct GoldenDouble: Encodable {
    let bits: String
    let value: Double

    init(_ value: Double) {
        precondition(value.isFinite, "golden data must be finite")
        let hex = String(value.bitPattern, radix: 16)
        self.bits = String(repeating: "0", count: 16 - hex.count) + hex
        self.value = value
    }
}

func fail(_ message: String, code: Int32) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(code)
}

/// ISO-8601 instants in UTC with whole seconds. Every case input uses these, so both
/// languages read exactly the same instant.
enum ISO {
    private static let formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter
    }()

    static func string(_ date: Date) -> String {
        precondition(date.timeIntervalSince1970 == date.timeIntervalSince1970.rounded(), "instants must be whole seconds")
        return formatter.string(from: date)
    }

    static func date(_ text: String) -> Date {
        guard let date = formatter.date(from: text) else { fatalError("bad ISO-8601 instant: \(text)") }
        return date
    }
}
