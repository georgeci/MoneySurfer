package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.getDatabaseBuilder
import com.georgeci.moneysurfer.data.db.getRoomDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import org.koin.core.module.Module
import org.koin.dsl.module

actual val sharedPlatformModule: Module = module {
    single<MoneySurferDatabase> { getRoomDatabase(getDatabaseBuilder()) }
    single { createDataStore() }
    single { AppInfo(version = readVersionName(), versionCode = 1) }
}

private fun readVersionName(): String =
    AppInfo::class.java.`package`?.implementationVersion ?: "0.1.0"
