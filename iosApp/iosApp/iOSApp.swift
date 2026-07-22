import SwiftUI
import FirebaseCore
import FirebaseCrashlytics

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
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
            options.apiKey = "AIzaSyDUMMYKEY0000000000000000000000000"
            options.projectID = "demo-moneysurfer"
            options.bundleID = Bundle.main.bundleIdentifier ?? "com.georgeci.moneysurfer"
            FirebaseApp.configure(options: options)
        } else {
            FirebaseApp.configure()
        }
        return true
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
