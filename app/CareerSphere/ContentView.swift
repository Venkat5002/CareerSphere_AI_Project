import SwiftUI

/// Shows a start-up screen until the backend and the web app answer, then the app itself.
struct ContentView: View {
    private enum Phase: Equatable {
        case waiting(String)
        case ready
        case failed(String)
    }

    @State private var phase: Phase = .waiting("Connecting to CareerSphere…")
    @State private var attempt = 0

    var body: some View {
        ZStack {
            Color("Paper").ignoresSafeArea()
            switch phase {
            case .ready:
                WebView(url: AppConfig.webApp)
                    .ignoresSafeArea(edges: .bottom)
            case .waiting(let message):
                StartupView(message: message, showSpinner: true, retry: nil)
            case .failed(let message):
                StartupView(message: message, showSpinner: false, retry: { attempt += 1 })
            }
        }
        .task(id: attempt) { await waitForServers() }
    }

    /// Polls the backend health check and the web app until both answer, or gives up.
    private func waitForServers() async {
        phase = .waiting("Connecting to CareerSphere…")
        let deadline = Date().addingTimeInterval(AppConfig.startupTimeout)
        while Date() < deadline {
            let backendUp = await Self.isUp(AppConfig.backendHealth)
            let webUp = await Self.isUp(AppConfig.webApp)
            if backendUp && webUp {
                phase = .ready
                return
            }
            phase = .waiting(backendUp
                ? "Starting the web app…"
                : "Starting the backend…\nThe first start can take a few minutes.")
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if Task.isCancelled { return }
        }
        phase = .failed("""
            CareerSphere's servers are not running.

            In Terminal, go to the project folder and run ./run.sh, \
            then press Try again.
            """)
    }

    private static func isUp(_ url: URL) async -> Bool {
        var request = URLRequest(url: url)
        request.timeoutInterval = 2
        request.cachePolicy = .reloadIgnoringLocalCacheData
        guard let result = try? await URLSession.shared.data(for: request),
              let http = result.1 as? HTTPURLResponse else { return false }
        return http.statusCode == 200
    }
}

/// Logo, a status line, and either a spinner or a Try again button.
private struct StartupView: View {
    let message: String
    let showSpinner: Bool
    let retry: (() -> Void)?

    var body: some View {
        VStack(spacing: 28) {
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(maxWidth: 220)
                .clipShape(RoundedRectangle(cornerRadius: 36, style: .continuous))
            if showSpinner {
                ProgressView()
            }
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(Color("Ink"))
                .padding(.horizontal, 32)
            if let retry {
                Button("Try again", action: retry)
                    .buttonStyle(.borderedProminent)
                    .tint(Color("Ink"))
            }
        }
        .padding()
    }
}
