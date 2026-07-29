package com.georgeci.moneysurfer.data.remote

import android.app.Application
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.storage.appDataDir
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Initializes the default `FirebaseApp` for the desktop (JVM) host.
 *
 * Android gets this for free from `google-services.json` + the Google Services
 * plugin, and iOS from `GoogleService-Info.plist` via `FirebaseApp.configure()`
 * ([iosApp/iosApp/iOSApp.swift](../../../../../../../../iosApp/iosApp/iOSApp.swift)).
 * The JVM has neither, so the default app has to be built by hand — without it
 * the first `Firebase.firestore` read inside Koin fails the whole graph with
 * "Default FirebaseApp is not initialized in this process".
 *
 * Must run **before** `initKoin`: `RemoteDataModule` resolves `Firebase.firestore`
 * / `Firebase.auth` lazily, but the first resolution happens while the app graph
 * is being built.
 *
 * Two option sources, mirroring the iOS host:
 * - Emulator runs (`MS_USE_EMULATOR=true`) use demo-project options — no
 *   credentials needed, the `demo-` project prefix puts the Firebase tooling in
 *   demo mode. The project id follows `FIREBASE_PROJECT_ID` when set, matching
 *   the `scripts/firebase` entry points. This is the documented desktop flow,
 *   see [docs/testing/firebase-emulator.md](../../../../../../../../docs/testing/firebase-emulator.md).
 * - Everything else reads `MS_FIREBASE_APP_ID` / `MS_FIREBASE_API_KEY` /
 *   `MS_FIREBASE_PROJECT_ID` from the environment. Desktop is a developer-only
 *   host with no shipped release, so real project credentials stay out of the
 *   repo (`google-services.json` is git-ignored) and are supplied per developer.
 *
 * Idempotent — a second call is a no-op.
 */
fun initializeDesktopFirebase() {
    if (initialized) return
    // The platform hook has to be installed before any FirebaseApp work: the java-sdk routes all
    // persistence (installation ids, auth tokens) and logging through it and has no default.
    FirebasePlatform.initializeFirebasePlatform(
        DesktopFirebasePlatform(File(appDataDir(), STORE_FILE_NAME)),
    )
    // `android.app.Application` here is the stub shipped *by* `firebase-java-sdk`, not the real
    // Android class. gitlive's JVM artifacts are Android sources repackaged, so they cast this
    // argument to Android types and the java-sdk supplies JVM-side stand-ins. It has to be
    // `Application`, not its `Context` supertype: Firestore's ComponentProvider casts down to
    // `Application` to register lifecycle callbacks, and a plain `Context` panics its async
    // queue with a ClassCastException on the first read.
    Firebase.initialize(Application(), desktopFirebaseOptions())
    initialized = true
}

@Volatile
private var initialized = false

private fun desktopFirebaseOptions(): FirebaseOptions =
    if (defaultUseEmulator()) emulatorOptions() else configuredOptions()

/**
 * Demo-project options for emulator runs. The emulator ignores the values, but
 * `FirebaseInstallations` still validates the API key's *format* — it must be 39
 * characters starting with "AI", or launch dies on an installations exception.
 * Same dummy shape as the iOS host (issue #219).
 */
private fun emulatorOptions(): FirebaseOptions = FirebaseOptions(
    applicationId = "1:000000000000:android:0000000000000000",
    apiKey = "AIza" + "0".repeat(API_KEY_LENGTH - "AIza".length),
    projectId = System.getenv(ENV_EMULATOR_PROJECT_ID)?.takeIf { it.isNotBlank() }
        ?: EMULATOR_PROJECT_ID,
    gcmSenderId = "000000000000",
)

private fun configuredOptions(): FirebaseOptions = FirebaseOptions(
    applicationId = requiredEnv(ENV_APP_ID),
    apiKey = requiredEnv(ENV_API_KEY),
    projectId = requiredEnv(ENV_PROJECT_ID),
)

private fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: error(
        "Desktop Firebase is not configured: set $ENV_APP_ID, $ENV_API_KEY and $ENV_PROJECT_ID " +
            "(values from the Firebase console), or run against the emulator with " +
            "MS_USE_EMULATOR=true. Missing: $name.",
    )

/**
 * Key-value store + logging sink the java-sdk needs in place of Android's
 * `SharedPreferences`. Backed by a properties file in the app-data directory so
 * the installation id and refresh tokens survive a restart, exactly as they do
 * on Android.
 */
private class DesktopFirebasePlatform(private val storeFile: File) : FirebasePlatform() {

    private val log = Logger.withTag(TAG)

    /**
     * Guards [values] together with the file write. Firebase calls in on several
     * background executors at once — `FirebaseInstallations` and the heartbeat
     * storage both write during startup — and two unsynchronized [flush] calls
     * would each truncate and rewrite the same file concurrently.
     */
    private val lock = Any()

    private val values = Properties().apply {
        if (storeFile.exists()) {
            storeFile.inputStream().use(::load)
        }
    }

    override fun store(key: String, value: String) {
        synchronized(lock) {
            values.setProperty(key, value)
            flush()
        }
    }

    override fun retrieve(key: String): String? = synchronized(lock) { values.getProperty(key) }

    override fun clear(key: String) {
        synchronized(lock) {
            values.remove(key)
            flush()
        }
    }

    override fun log(msg: String) {
        log.d { msg }
    }

    /**
     * Where Firestore puts its local persistence SQLite file. Only the *parent*
     * is created — calling `mkdirs()` on the returned path makes a directory
     * where the database file belongs, and SQLite then fails with
     * `SQLITE_CANTOPEN`, panicking Firestore's async queue on first use.
     */
    override fun getDatabasePath(name: String): File =
        File(storeFile.parentFile, name).also { it.parentFile?.mkdirs() }

    /**
     * Serialize to a sibling temp file, then rename over the target. Writing
     * straight into [storeFile] truncates it first, so a crash mid-write — or a
     * second writer — leaves a partial file that fails `Properties.load` on the
     * next launch, which would throw out of `initializeDesktopFirebase()` before
     * the window opens. The temp file is a sibling, so the move stays within one
     * file system and can be atomic.
     */
    private fun flush() {
        val temp = File(storeFile.parentFile, "${storeFile.name}.tmp")
        temp.outputStream().use { values.store(it, null) }
        Files.move(
            temp.toPath(),
            storeFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private companion object {
        const val TAG = "DesktopFirebase"
    }
}

private const val STORE_FILE_NAME = "firebase.properties"

/**
 * Shared emulator project id — keep in sync with
 * `EmulatorFirebaseConfig.DEFAULT_PROJECT_ID` in `:data-test-fixtures`, which
 * this production module deliberately does not depend on.
 */
private const val EMULATOR_PROJECT_ID = "demo-moneysurfer"

/**
 * Same override the `scripts/firebase` entry points honour. The emulator only
 * serves the project it was booted with, so a developer who starts it on a
 * different id has to be able to point the desktop host at that id too —
 * otherwise the app silently reads an empty namespace.
 */
private const val ENV_EMULATOR_PROJECT_ID = "FIREBASE_PROJECT_ID"
private const val API_KEY_LENGTH = 39
private const val ENV_APP_ID = "MS_FIREBASE_APP_ID"
private const val ENV_API_KEY = "MS_FIREBASE_API_KEY"
private const val ENV_PROJECT_ID = "MS_FIREBASE_PROJECT_ID"
