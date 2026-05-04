package com.georgeci.moneysurfer.di

import android.content.Context
import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.getDatabaseBuilder
import com.georgeci.moneysurfer.data.db.getRoomDatabase
import com.georgeci.moneysurfer.data.repository.FirebaseCrashReporter
import com.georgeci.moneysurfer.data.remote.androidRemoteDataModule
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.sync.db.SyncDatabase
import com.georgeci.moneysurfer.sync.db.getSyncDatabaseBuilder
import com.georgeci.moneysurfer.sync.db.getSyncRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    includes(androidRemoteDataModule)

    single<MoneySurferDatabase> {
        val builder = getDatabaseBuilder(context = get())
        getRoomDatabase(builder)
    }
    single<SyncDatabase> { getSyncRoomDatabase(getSyncDatabaseBuilder(context = get())) }
    single<CrashReporter> { FirebaseCrashReporter() }
    single { createDataStore(context = get()) }
    single {
        val context: Context = get()
        AppInfo(
            version = readVersionName(context),
            versionCode = readVersionCode(context),
        )
    }
}

private fun readVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"

@Suppress("DEPRECATION")
private fun readVersionCode(context: Context): Int =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }.getOrDefault(1)
