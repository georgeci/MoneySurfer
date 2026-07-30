package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.backup.JvmAppRestarter
import com.georgeci.moneysurfer.data.backup.JvmBackupStorageLocator
import com.georgeci.moneysurfer.data.config.createDebugConfigSource
import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.getDatabaseBuilder
import com.georgeci.moneysurfer.data.db.getRoomDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.backup.AppRestarter
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import org.koin.core.module.Module
import org.koin.dsl.module

actual val sharedPlatformModule: Module = module {
    includes(applicationScopeModule)
    // The desktop host is developer-only today and starts Koin with `isDebug = true`
    // (`composeApp/.../main.kt`), so the destructive fallback stays on here. Revisit together
    // with that flag if a packaged desktop release ships — see
    // docs/architecture/persistence.md → "Room schema versioning".
    single<MoneySurferDatabase> {
        getRoomDatabase(builder = getDatabaseBuilder(), allowDestructiveMigration = true)
    }
    single { createDataStore() }
    // Own DataStore file, created in the factory rather than bound — see the Android actual.
    single<DebugConfigSource> { createDebugConfigSource(scope = get()) }
    single { AppInfo(version = readVersionName(), versionCode = 1) }
    single<BackupStorageLocator> { JvmBackupStorageLocator() }
    single<AppRestarter> { JvmAppRestarter() }
}

private fun readVersionName(): String =
    AppInfo::class.java.`package`?.implementationVersion ?: "0.1.0"
