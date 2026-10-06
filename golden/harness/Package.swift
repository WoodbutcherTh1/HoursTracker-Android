// swift-tools-version:5.9
import PackageDescription

// Golden-file generator. Run it only through golden/run.sh (see golden/README.md).
// The test cases and the iOS source links arrive in milestone M1.
let package = Package(
    name: "GoldenGen",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(name: "GoldenGen", path: "Sources/GoldenGen")
    ]
)
