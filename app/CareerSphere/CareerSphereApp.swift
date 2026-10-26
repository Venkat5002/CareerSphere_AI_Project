import SwiftUI

/// CareerSphere as a native Mac (and iPhone / iPad) app.
///
/// Press Run in Xcode. The first build step runs scripts/start-servers.sh, which starts MySQL and
/// opens the backend and frontend in their own Terminal windows, then waits until both answer.
/// The app then shows the CareerSphere web app in a native window.
@main
struct CareerSphereApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        #if os(macOS)
        .defaultSize(width: 1280, height: 840)
        #endif
    }
}

/// Where the servers run. On a Mac, and in the iOS Simulator, localhost is this Mac.
enum AppConfig {
    static let webApp = URL(string: "http://localhost:5173")!
    static let backendHealth = URL(string: "http://localhost:8080/api/health")!
    /// How long the app waits for the servers before showing help.
    static let startupTimeout: TimeInterval = 360
}
