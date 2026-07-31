package com.georgeci.moneysurfer.data.remote

import co.touchlab.kermit.Logger
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.FirebaseOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Which Firebase project this host talks to. [useEmulator] and [env] are parameters
 * rather than direct reads so the branching — in particular the `FIREBASE_PROJECT_ID`
 * override, which is what keeps the desktop host on the same namespace the emulator
 * was booted with — is reachable from tests. Production always takes the defaults.
 */
internal fun desktopFirebaseOptions(
    useEmulator: Boolean = defaultUseEmulator(),
    env: (String) -> String? = System::getenv,
): FirebaseOptions = if (useEmulator) emulatorOptions(env) else configuredOptions(env)

/**
 * Demo-project options for emulator runs. The emulator ignores the values, but
 * `FirebaseInstallations` still validates the API key's *format* — it must be 39
 * characters starting with "AI", or launch dies on an installations exception.
 * Same dummy shape as the iOS host (issue #219).
 */
private fun emulatorOptions(env: (String) -> String?): FirebaseOptions = FirebaseOptions(
    applicationId = "1:000000000000:android:0000000000000000",
    apiKey = "AIza" + "0".repeat(API_KEY_LENGTH - "AIza".length),
    projectId = env(ENV_EMULATOR_PROJECT_ID)?.takeIf { it.isNotBlank() }
        ?: EMULATOR_PROJECT_ID,
    gcmSenderId = "000000000000",
)

private fun configuredOptions(env: (String) -> String?): FirebaseOptions = FirebaseOptions(
    applicationId = requiredEnv(ENV_APP_ID, env),
    apiKey = requiredEnv(ENV_API_KEY, env),
    projectId = requiredEnv(ENV_PROJECT_ID, env),
)

private fun requiredEnv(name: String, env: (String) -> String?): String =
    env(name)?.takeIf { it.isNotBlank() } ?: error(
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
internal class DesktopFirebasePlatform(private val storeFile: File) : FirebasePlatform() {

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
