package com.georgeci.moneysurfer.offline

import android.app.Application
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.offline.di.offlineWiring
import org.koin.android.ext.koin.androidContext

class MoneySurferOfflineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(isDebug = BuildConfig.DEBUG, extraModules = offlineWiring) {
            androidContext(this@MoneySurferOfflineApplication)
        }
    }
}
