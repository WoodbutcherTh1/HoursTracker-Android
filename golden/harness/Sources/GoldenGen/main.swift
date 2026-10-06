import Foundation

// Golden-file generator.
//
// Usage:
//   GoldenGen --env <il|ru|utc> --out <directory> [--lenient]
//       writes every case group for one pinned environment
//       (--lenient is for provisional runs outside macOS only: see Environment.swift)
//   GoldenGen --manifest --out <directory> --ios-sha <sha>
//       writes manifest.json for the files already in the directory
//
// Anything else on the command line (for example `-AppleLocale he_IL`) is for the system's
// own use, which is how run.sh pins the locale on macOS; it is ignored here.
// Run it through golden/run.sh rather than directly.

var options: [String: String] = [:]
var wantsManifest = false
var lenient = false
let arguments = Array(CommandLine.arguments.dropFirst())
var position = 0
while position < arguments.count {
    switch arguments[position] {
    case "--env", "--out", "--ios-sha":
        guard position + 1 < arguments.count else { fail("missing value for \(arguments[position])", code: 64) }
        options[arguments[position]] = arguments[position + 1]
        position += 2
    case "--manifest":
        wantsManifest = true
        position += 1
    case "--lenient":
        lenient = true
        position += 1
    default:
        position += 1
    }
}

guard let outPath = options["--out"] else {
    fail("usage: GoldenGen --env <il|ru|utc> --out <dir>   |   GoldenGen --manifest --out <dir> --ios-sha <sha>", code: 64)
}
let outDirectory = URL(fileURLWithPath: outPath, isDirectory: true)

do {
    try FileManager.default.createDirectory(at: outDirectory, withIntermediateDirectories: true)

    if wantsManifest {
        guard let sha = options["--ios-sha"] else { fail("--manifest needs --ios-sha", code: 64) }
        try writeManifest(outDirectory: outDirectory, iosCommit: sha)
    } else {
        guard let envID = options["--env"], let environment = GoldenEnvironment.all.first(where: { $0.id == envID }) else {
            fail("--env must be one of: \(GoldenEnvironment.all.map(\.id).joined(separator: ", "))", code: 64)
        }
        let context = environment.pinAndVerify(outDirectory: outDirectory, lenient: lenient)
        print("Environment \(context.info.id): \(context.info.timeZone), first weekday \(context.info.firstWeekday), "
            + "minimal days \(context.info.minimumDaysInFirstWeek), system locale \(context.info.systemLocale)")
        // Case groups are registered here as they are added.
        let groups: [(name: String, run: (GoldenContext) throws -> Void)] = []
        for group in groups {
            try group.run(context)
        }
        if groups.isEmpty { print("  (no case groups yet)") }
    }
} catch {
    fail("GoldenGen failed: \(error)", code: 1)
}
