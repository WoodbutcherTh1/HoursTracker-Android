import Foundation

// Golden-file generator (skeleton).
//
// Usage: GoldenGen <output-directory> <ios-commit-sha>
//
// Writes `manifest.json` into the output directory. The test cases that produce
// the golden data files arrive in milestone M1, after the case list is reviewed.

struct Manifest: Encodable {
    /// Bumped when the file format changes.
    let formatVersion: Int
    /// The iOS commit the data was generated from (the checkout must be clean).
    let iosCommit: String
    let generatedAt: String
    /// Golden data files written by this run, sorted by name.
    let caseFiles: [String]
}

let arguments = CommandLine.arguments
guard arguments.count == 3 else {
    FileHandle.standardError.write(Data("usage: GoldenGen <output-directory> <ios-commit-sha>\n".utf8))
    exit(64)
}

let outputDirectory = URL(fileURLWithPath: arguments[1], isDirectory: true)
let iosCommit = arguments[2]

do {
    try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)

    let manifest = Manifest(
        formatVersion: 1,
        iosCommit: iosCommit,
        generatedAt: ISO8601DateFormatter().string(from: Date()),
        caseFiles: []
    )

    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    let data = try encoder.encode(manifest)
    try data.write(to: outputDirectory.appendingPathComponent("manifest.json"), options: .atomic)
    print("Wrote manifest for iOS commit \(iosCommit) (no cases yet).")
} catch {
    FileHandle.standardError.write(Data("GoldenGen failed: \(error)\n".utf8))
    exit(1)
}
