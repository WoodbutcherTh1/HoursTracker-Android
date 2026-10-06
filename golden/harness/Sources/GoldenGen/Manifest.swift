import Foundation

/// `manifest.json`: which iOS commit and platform produced the data, and which files exist.
struct Manifest: Encodable {
    struct File: Encodable {
        let name: String
        let group: String
        let environment: String
    }

    /// Bumped when the file format changes.
    let formatVersion: Int
    /// The iOS commit the data was generated from (the checkout must be clean).
    let iosCommit: String
    let generatedAt: String
    /// "darwin" data is authoritative. "linux" data is provisional and must never be committed.
    let platform: String
    let authoritative: Bool
    let osVersion: String
    let files: [File]
}

func writeManifest(outDirectory: URL, iosCommit: String) throws {
    let names = try FileManager.default.contentsOfDirectory(atPath: outDirectory.path)
        .filter { $0.hasSuffix(".json") && $0 != "manifest.json" }
        .sorted()

    let files: [Manifest.File] = try names.map { name in
        let data = try Data(contentsOf: outDirectory.appendingPathComponent(name))
        guard
            let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let group = object["group"] as? String,
            let environment = (object["environment"] as? [String: Any])?["id"] as? String
        else { fail("\(name) is not a golden file.", code: 65) }
        return Manifest.File(name: name, group: group, environment: environment)
    }

    let manifest = Manifest(
        formatVersion: 1,
        iosCommit: iosCommit,
        generatedAt: ISO8601DateFormatter().string(from: Date()),
        platform: GoldenEnvironment.platform,
        authoritative: GoldenEnvironment.platform == "darwin",
        osVersion: ProcessInfo.processInfo.operatingSystemVersionString,
        files: files
    )

    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    try encoder.encode(manifest).write(to: outDirectory.appendingPathComponent("manifest.json"), options: .atomic)
    print("Wrote manifest.json: \(files.count) file(s), platform \(manifest.platform).")
}
