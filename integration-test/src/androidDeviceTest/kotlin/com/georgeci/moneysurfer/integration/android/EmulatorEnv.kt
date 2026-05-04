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
 *  - When [appName] is `null` (default), uses the *default* `FirebaseApp`.
 *    When non-null, initialises (or reuses) a *named* `FirebaseApp` so two
 *    `EmulatorEnv` instances in the same process can host independent
 *    Firestore + Auth clients — required for multi-client convergence tests
 *    where each side needs its own auth session.
 *  - `useEmulator()` MUST be called before the first SDK operation. A second
 *    call is a no-op once the SDK has locked the endpoint, so re-construction
 *    inside the same process tolerates re-init.
 *
 * Lifecycle: build per test (`@Before`), call [signOut] between sub-cases if
 * needed, [delete] in `@After` so the next test gets a clean Firebase state.
 */
class EmulatorEnv(
    private val appName: String? = null,
    firestoreHost: String = "10.0.2.2",
    firestorePort: Int = 8080,
    authHost: String = "10.0.2.2",
    authPort: Int = 9099,
) {
    private val nativeApp: FirebaseApp = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:0:android:0") // emulator doesn't validate; placeholder
            .setProjectId(EMULATOR_PROJECT_ID)
            .setApiKey("emulator-api-key") // ditto
            .build()
        if (appName == null) {
            runCatching { FirebaseApp.getInstance() }.getOrNull()
                ?: FirebaseApp.initializeApp(context, options)
        } else {
            // Local val captures the smart-cast — the compiler won't propagate
            // a non-null smart-cast on a constructor-property through a closure.
            val name = appName
            runCatching { FirebaseApp.getInstance(name) }.getOrNull()
                ?: FirebaseApp.initializeApp(context, options, name)
        }
    }

    /**
     * Gitlive wrapper around [nativeApp]. We hold the native app for [delete]
     * (gitlive's `FirebaseApp.delete` is suspend, but we need a synchronous
     * close in `@After`) and the gitlive wrapper for `Firebase.firestore(app)`
     * / `Firebase.auth(app)` accessor lookup.
     */
    private val gitliveApp: dev.gitlive.firebase.FirebaseApp = run {
        // Local val so the smart-cast to non-null reaches the call site —
        // the compiler doesn't propagate a smart-cast through a property's
        // backing field into a sibling property's initializer.
        val name = appName
        if (name == null) Firebase.app else Firebase.app(name)
    }

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
    }
}
