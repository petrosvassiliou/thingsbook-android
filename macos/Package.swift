// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "ThingsbookMac",
    defaultLocalization: "en",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .library(
            name: "ThingsbookCore",
            targets: ["ThingsbookCore"]
        ),
        .executable(
            name: "Thingsbook",
            targets: ["ThingsbookMac"]
        )
    ],
    targets: [
        .target(
            name: "ThingsbookCore",
            path: "Sources/ThingsbookCore",
            resources: [
                .process("Resources")
            ],
            linkerSettings: [
                .linkedLibrary("sqlite3"),
                .linkedLibrary("z"),
                .linkedFramework("Security"),
                .linkedFramework("EventKit"),
                .linkedFramework("LocalAuthentication"),
                .linkedFramework("UserNotifications"),
                .linkedFramework("AppKit")
            ]
        ),
        .executableTarget(
            name: "ThingsbookMac",
            dependencies: ["ThingsbookCore"],
            path: "Sources/ThingsbookMac",
            linkerSettings: [
                .linkedFramework("SwiftUI"),
                .linkedFramework("AppKit"),
                .linkedFramework("UniformTypeIdentifiers")
            ]
        ),
        .testTarget(
            name: "ThingsbookCoreTests",
            dependencies: ["ThingsbookCore"],
            path: "Tests/ThingsbookCoreTests"
        )
    ]
)
