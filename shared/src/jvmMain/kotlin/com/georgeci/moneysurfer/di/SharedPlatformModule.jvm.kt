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
    single<MoneySurferDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single { createDataStore() }
    // Own DataStore file, created in the factory rather than bound — see the Android actual.
    single<DebugConfigSource> { createDebugConfigSource() }
    single { AppInfo(version = readVersionName(), versionCode = 1) }
    single<BackupStorageLocator> { JvmBackupStorageLocator() }
    single<AppRestarter> { JvmAppRestarter() }
}

private fun readVersionName(): String =
    AppInfo::class.java.`package`?.implementationVersion ?: "0.1.0"
