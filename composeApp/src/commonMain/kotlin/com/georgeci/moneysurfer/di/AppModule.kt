package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.data.di.LocalDataModule
import com.georgeci.moneysurfer.data.di.RemoteDataModule
import com.georgeci.moneysurfer.data.di.SyncImplModule
import com.georgeci.moneysurfer.domain.di.DomainModule
import com.georgeci.moneysurfer.feature.account.di.AccountModule
import com.georgeci.moneysurfer.feature.category.di.CategoryModule
import com.georgeci.moneysurfer.feature.dashboard.di.DashboardModule
import com.georgeci.moneysurfer.feature.login.di.LoginModule
import com.georgeci.moneysurfer.feature.settings.di.SettingsModule
import com.georgeci.moneysurfer.feature.transaction.di.TransactionModule
import com.georgeci.moneysurfer.feature.workspace.di.WorkspaceModule
import com.georgeci.moneysurfer.navigation.di.NavigationModule
import com.georgeci.moneysurfer.shared.di.SharedModule
import com.georgeci.moneysurfer.sync.internal.di.SyncModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication
@Module(
    includes = [
        LocalDataModule::class,
        RemoteDataModule::class,
        SyncImplModule::class,
        DomainModule::class,
        SharedModule::class,
        NavigationModule::class,
        SyncModule::class,
        SettingsModule::class,
        TransactionModule::class,
        DashboardModule::class,
        LoginModule::class,
        WorkspaceModule::class,
        CategoryModule::class,
        AccountModule::class,
    ],
)
@ComponentScan("com.georgeci.moneysurfer")
class AppModule
