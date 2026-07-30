package com.georgeci.moneysurfer.di

import android.content.Context
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.backup.AndroidAppRestarter
import com.georgeci.moneysurfer.data.backup.AndroidBackupStorageLocator
import com.georgeci.moneysurfer.data.config.createDebugConfigSource
import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.getDatabaseBuilder
import com.georgeci.moneysurfer.data.db.getRoomDatabase
import com.georgeci.moneysurfer.data.platform.isDebuggableBuild
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.backup.AppRestarter
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import org.koin.core.module.Module
import org.koin.dsl.module

actual val sharedPlatformModule: Module = module {
    includes(applicationScopeModule)
    single<MoneySurferDatabase> {
        val context: Context = get()
        // Debuggable APKs may drop the local database on a schema change; release APKs must
        // migrate. See docs/architecture/persistence.md → "Room schema versioning".
        getRoomDatabase(
            builder = getDatabaseBuilder(context = context),
            allowDestructiveMigration = context.isDebuggableBuild(),
        )
    }
    single { createDataStore(context = get()) }
    // Debug overrides get their own DataStore file, created inside the factory rather than
    // bound: a second unqualified `DataStore<Preferences>` would collide with the one above
    // and both layers would read the same file. Release APKs resolve `Empty`.
    single<DebugConfigSource> { createDebugConfigSource(context = get(), scope = get()) }
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
