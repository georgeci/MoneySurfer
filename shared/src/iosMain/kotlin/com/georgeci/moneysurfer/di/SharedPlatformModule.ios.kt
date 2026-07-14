package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.data.backup.IosAppRestarter
import com.georgeci.moneysurfer.data.backup.IosBackupStorageLocator
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

actual val sharedPlatformModule: Module = module {
    single<MoneySurferDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single { createDataStore() }
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
