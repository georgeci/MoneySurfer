package com.georgeci.moneysurfer.data.remote

import android.app.Application
import com.georgeci.moneysurfer.domain.storage.appDataDir
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import java.io.File

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
 * This file holds only the install itself, which installs process-global SDK state
 * that a test can neither install nor undo, and is therefore excluded from the
 * coverage report — see the `kover` filters in the root
 * [build.gradle.kts](../../../../../../../../build.gradle.kts). Everything it
 * *decides* — which project, which credentials, how the store behaves — lives in
 * [FirebaseBootstrap.jvm.kt] and [DesktopAppCheck.jvm.kt], which are covered
 * normally. Keep it that way: logic added here becomes untested by construction.
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
    val useEmulator = defaultUseEmulator()
    val options = desktopFirebaseOptions(useEmulator = useEmulator)
    Firebase.initialize(Application(), options)
    // Skipped against the emulator: it does not verify App Check tokens, and there is no
    // project to register a debug secret with. See DesktopAppCheck.jvm.kt.
    if (!useEmulator) {
        installDesktopAppCheck(options)
    }
    initialized = true
}

@Volatile
private var initialized = false

internal const val STORE_FILE_NAME = "firebase.properties"
