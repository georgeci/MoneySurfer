package com.georgeci.moneysurfer

import android.app.Application
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
        }

        initKoin(extraModules = onlineWiring) {
            androidContext(this@MoneySurferApplication)
        }
    }
}
