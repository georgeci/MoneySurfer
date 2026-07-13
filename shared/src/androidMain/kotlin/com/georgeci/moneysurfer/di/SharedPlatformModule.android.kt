package com.georgeci.moneysurfer.di

import android.content.Context
import com.georgeci.moneysurfer.data.backup.AndroidAppRestarter
import com.georgeci.moneysurfer.data.backup.AndroidBackupStorageLocator
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
    single<MoneySurferDatabase> {
        val builder = getDatabaseBuilder(context = get())
        getRoomDatabase(builder)
    }
    single { createDataStore(context = get()) }
    single {
        val context: Context = get()
        AppInfo(
            version = readVersionName(context),
            versionCode = readVersionCode(context),
        )
    }
    single<BackupStorageLocator> { AndroidBackupStorageLocator(context = get()) }
    single<AppRestarter> { AndroidAppRestarter(context = get()) }
}

private fun readVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0.1.0"

@Suppress("DEPRECATION")
private fun readVersionCode(context: Context): Int =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }.getOrDefault(1)
