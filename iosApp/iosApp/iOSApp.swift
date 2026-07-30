import SwiftUI
import FirebaseAppCheck
import FirebaseCore
import FirebaseCrashlytics

/// Firebase ships `AppAttestProvider` but — unlike DeviceCheck — no factory for it, and there is
/// no implicit fallback: with no factory registered `FIRAppCheck` logs "without a provider
/// factory" and App Check is never instantiated at all. Hence this adapter.
private final class AppAttestProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Must precede `FirebaseApp.configure()` — Firebase reads the factory during
        // configuration, and a provider installed afterwards is ignored for the first calls.
        installAppCheckProviderFactory()

        if shouldUseFirebaseEmulator() {
            let options = FirebaseOptions(
                googleAppID: "1:000000000000:ios:0000000000000000",
                gcmSenderID: "000000000000"
            )
            // FirebaseInstallations (started via Analytics during configureCore)
            // validates the API key format and throws an uncaught
            // `com.firebase.installations` NSException on launch when it doesn't
            // match — the key must be exactly 39 chars and start with "A". A
            // literal "fake-api-key" crashed every E2E flow before the sign-in
            // screen ever appeared (issue #219). The emulator ignores the key's
            // value, so any format-valid dummy works.
            options.apiKey = ["AI", "za", String(repeating: "0", count: 35)].joined()
            options.projectID = "demo-moneysurfer"
            options.bundleID = Bundle.main.bundleIdentifier ?? "com.georgeci.moneysurfer"
            FirebaseApp.configure(options: options)
        } else {
            FirebaseApp.configure()
        }
        return true
    }

    /// App Check attests that the caller is the real MoneySurfer build, which Firestore rules
    /// cannot express — they only know who the user is.
    ///
    /// Skipped against the emulator: it does not verify tokens, and the demo project has no
    /// place to register a debug secret. App Attest needs a real device and a provisioned
    /// bundle id, so the Simulator and debug builds fall back to the debug provider, whose
    /// secret must be registered under App Check → Apps → Manage debug tokens.
    private func installAppCheckProviderFactory() {
        guard !shouldUseFirebaseEmulator() else { return }

        #if targetEnvironment(simulator) || DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(AppAttestProviderFactory())
        #endif
    }

    private func shouldUseFirebaseEmulator() -> Bool {
        let env = ProcessInfo.processInfo.environment["MS_USE_EMULATOR"]
        let plist = Bundle.main.object(forInfoDictionaryKey: "MS_USE_EMULATOR") as? String
        return env.isTrue || plist.isTrue
    }
}

private extension Optional where Wrapped == String {
    var isTrue: Bool {
        guard let value = self else { return false }
        return value == "1" || value.uppercased() == "YES" || value.lowercased() == "true"
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
