import Foundation

// Minimal stand-ins for types the linked iOS files refer to but that cannot (or need not)
// be linked: UI string tables, the app view model, and a SwiftUI-only model. They contain no
// pay logic. Everything that decides a number comes from the real iOS files.

enum L10n {
    static var dayTypeRegular: String { "regular" }
    static var dayTypeRestDay: String { "restDay" }
    static var dayTypeHoliday: String { "holiday" }
    static var dayTypeSick: String { "sick" }
    static var leaveVacation: String { "vacation" }
    static var leaveRecuperation: String { "recuperation" }
}

enum AppLocale {
    static var resolvedLocale: Locale { Locale(identifier: "en_US_POSIX") }
    static func tr(_ key: String) -> String { key }
}

/// `AdditionalWorkplace.swift` imports SwiftUI, so it is replaced by this shape-compatible type.
/// The harness never creates additional workplaces.
struct AdditionalWorkplace: Codable, Equatable, Identifiable {
    var id: UUID
    var colorIndex: Int
    var settings: WorkplaceSettings
}

/// Stand-in for the real view model, which owns far more than the live-pay code needs.
/// `AppViewModel+LivePay.swift` (a real iOS file) only asks it for these two things.
final class AppViewModel {
    var settings: WorkplaceSettings
    var sessions: [WorkSession]

    init(settings: WorkplaceSettings, sessions: [WorkSession] = []) {
        self.settings = settings
        self.sessions = sessions
    }

    func workplaceSettings(for workplaceID: UUID?) -> WorkplaceSettings { settings }
    func workplaceSessions(for workplaceID: UUID?) -> [WorkSession] { sessions }
}
