package com.georgeci.moneysurfer

import android.app.Application
import com.georgeci.moneysurfer.appcheck.installAppCheckProvider
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.di.onlineWiring
import org.koin.android.ext.koin.androidContext

class MoneySurferApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Phase 4 — propagate the emulator toggle from BuildConfig (compile-time
        // flag, set via `-PuseEmulator=true` in androidApp/build.gradle.kts) into
        // the system property `FirebaseConfigImpl.android` reads. Must run BEFORE
        // Koin starts so the lazy `FirebaseConfig` single sees the right value
        // on first instantiation.
        if (BuildConfig.USE_EMULATOR) {
            System.setProperty("MS_USE_EMULATOR", "true")
        } else {
            // App Check attests that the caller is the real app, which Firestore rules cannot
            // express — they only know who the user is. Skipped against the emulator, which
            // does not verify tokens and has no project to register a debug secret with.
            //
            // Must run before the first Firestore/Auth call: the provider is installed on the
            // already-auto-initialized default FirebaseApp, and Koin resolves those lazily
            // below. The variant source sets pick the provider — debug secret vs Play Integrity.
            installAppCheckProvider()
        }

        initKoin(isDebug = BuildConfig.DEBUG, extraModules = onlineWiring) {
            androidContext(this@MoneySurferApplication)
        }
    }
}
