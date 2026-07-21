package com.georgeci.moneysurfer.integration.android

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore

/**
 * Boots Firebase against the local Emulator Suite and exposes the gitlive
 * wrappers production code consumes (`FirebaseFirestore`, `FirebaseAuth`).
 *
 * Conventions:
 *  - Emulator host is `10.0.2.2` (AVD's view of the developer host machine).
 *  - Default ports: Firestore `8080`, Auth `9099`. Match `firebase.json` →
 *    `emulators.firestore.port` / `emulators.auth.port`.
 *  - A *named* `FirebaseApp` is always used — never the default one. When
 *    [appName] is `null` a shared [DEFAULT_APP_NAME] is used; when non-null that
 *    exact name is used so two `EmulatorEnv` instances in the same process can
 *    host independent Firestore + Auth clients — required for multi-client
 *    convergence tests where each side needs its own auth session.
 *
 *    Why never the default app: initialising the *default* `FirebaseApp` eagerly
 *    runs `FirebaseApp.initializeAllApis()`, which pulls in Crashlytics and
 *    throws `IllegalStateException: The Crashlytics build ID is missing` in the
 *    instrumentation build (the Crashlytics SDK is on the classpath but its
 *    Gradle plugin — which injects the build-id resource — is not). That threw
 *    from `EmulatorEnv.<init>` in every `@Before` that re-initialised the default
 *    app after a prior test's `delete()`, failing the harness before any
 *    assertion ran. Named/secondary apps skip `initializeAllApis`, so they never
 *    touch Crashlytics.
 *  - `useEmulator()` MUST be called before the first SDK operation. A second
 *    call is a no-op once the SDK has locked the endpoint, so re-construction
 *    inside the same process tolerates re-init.
 *
 * Lifecycle: build per test (`@Before`), call [signOut] between sub-cases if
 * needed, [delete] in `@After` so the next test gets a clean Firebase state.
 */
class EmulatorEnv(
    appName: String? = null,
    firestoreHost: String = "10.0.2.2",
    firestorePort: Int = 8080,
    authHost: String = "10.0.2.2",
    authPort: Int = 9099,
) {
    /**
     * The FirebaseApp name to use. Always non-null (never the default app) so
     * `initializeAllApis()`/Crashlytics is never triggered — see the class kdoc.
     */
    private val effectiveAppName: String = appName ?: DEFAULT_APP_NAME

    private val nativeApp: FirebaseApp = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:0:android:0") // emulator doesn't validate; placeholder
            .setProjectId(EMULATOR_PROJECT_ID)
            .setApiKey("emulator-api-key") // ditto
            .build()
        runCatching { FirebaseApp.getInstance(effectiveAppName) }.getOrNull()
            ?: FirebaseApp.initializeApp(context, options, effectiveAppName)
    }

    /**
     * Gitlive wrapper around [nativeApp]. We hold the native app for [delete]
     * (gitlive's `FirebaseApp.delete` is suspend, but we need a synchronous
     * close in `@After`) and the gitlive wrapper for `Firebase.firestore(app)`
     * / `Firebase.auth(app)` accessor lookup.
     */
    private val gitliveApp: dev.gitlive.firebase.FirebaseApp = Firebase.app(effectiveAppName)

    val firestore: FirebaseFirestore = run {
        // Disable offline persistence so every read/write goes to the emulator server
        // synchronously. With persistence enabled, Task<Void> from set()/update() resolves
        // on local-cache write (not server ack), which causes subsequent operations that
        // require the document to exist on the server (e.g. update() after get()) to fail
        // with NOT_FOUND even though get() succeeds from cache.
        val nativeFs = com.google.firebase.firestore.FirebaseFirestore.getInstance(nativeApp)
        runCatching {
            nativeFs.firestoreSettings =
                com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(false)
                    .build()
        }
        Firebase.firestore(gitliveApp).also {
            runCatching { it.useEmulator(firestoreHost, firestorePort) }
        }
    }

    val auth: FirebaseAuth = Firebase.auth(gitliveApp).also {
        runCatching { it.useEmulator(authHost, authPort) }
    }

    val projectId: String = EMULATOR_PROJECT_ID

    suspend fun signOut() {
        auth.signOut()
    }

    fun delete() {
        nativeApp.delete()
    }

    companion object {
        // Must match the `--project` flag used to launch the emulator (see
        // `firestore-tests/package.json` — we share the same demo id so a single
        // emulator process can serve both Mocha and androidDeviceTest suites).
        const val EMULATOR_PROJECT_ID = "demo-moneysurfer"

        // Shared name for the single-client harness's FirebaseApp. Any non-default
        // name works; multi-client tests pass their own per-side names instead.
        const val DEFAULT_APP_NAME = "it-default"
    }
}
