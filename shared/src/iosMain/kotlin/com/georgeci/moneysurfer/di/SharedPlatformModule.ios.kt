package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.backup.IosAppRestarter
import com.georgeci.moneysurfer.data.backup.IosBackupStorageLocator
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
import platform.Foundation.NSBundle
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val sharedPlatformModule: Module = module {
    includes(applicationScopeModule)
    // Debug binaries may drop the local database on a schema change; release builds must
    // migrate. See docs/architecture/persistence.md → "Room schema versioning".
    single<MoneySurferDatabase> {
        getRoomDatabase(
            builder = getDatabaseBuilder(),
            allowDestructiveMigration = kotlin.native.Platform.isDebugBinary,
        )
    }
    single { createDataStore() }
    // Own DataStore file, created in the factory rather than bound — see the Android actual.
    single<DebugConfigSource> { createDebugConfigSource(scope = get()) }
    single {
        AppInfo(
            version = readVersionName(),
            versionCode = readVersionCode(),
        )
    }
    single<BackupStorageLocator> { IosBackupStorageLocator() }
    single<AppRestarter> { IosAppRestarter() }
}

private fun readVersionName(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: "0.1.0"

private fun readVersionCode(): Int =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
        ?.toIntOrNull()
        ?: 1
