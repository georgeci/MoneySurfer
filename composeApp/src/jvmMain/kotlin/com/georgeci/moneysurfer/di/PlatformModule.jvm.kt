package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.getDatabaseBuilder
import com.georgeci.moneysurfer.data.db.getRoomDatabase
import com.georgeci.moneysurfer.data.repository.NoOpCrashReporter
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.sync.db.SyncDatabase
import com.georgeci.moneysurfer.sync.db.getSyncDatabaseBuilder
import com.georgeci.moneysurfer.sync.db.getSyncRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<MoneySurferDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single<SyncDatabase> { getSyncRoomDatabase(getSyncDatabaseBuilder()) }
    single<CrashReporter> { NoOpCrashReporter() }
    single { createDataStore() }
    single { AppInfo(version = readVersionName(), versionCode = 1) }
}

private fun readVersionName(): String =
    AppInfo::class.java.`package`?.implementationVersion ?: "1.0.0"
