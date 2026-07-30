package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.data.di.LocalDataModule
import com.georgeci.moneysurfer.domain.di.DomainModule
import com.georgeci.moneysurfer.feature.account.di.AccountModule
import com.georgeci.moneysurfer.feature.budget.di.BudgetModule
import com.georgeci.moneysurfer.feature.category.di.CategoryModule
import com.georgeci.moneysurfer.feature.dashboard.di.DashboardModule
import com.georgeci.moneysurfer.feature.goal.di.GoalModule
import com.georgeci.moneysurfer.feature.insights.di.InsightsModule
import com.georgeci.moneysurfer.feature.login.di.LoginModule
import com.georgeci.moneysurfer.feature.settings.di.SettingsModule
import com.georgeci.moneysurfer.feature.transaction.di.TransactionModule
import com.georgeci.moneysurfer.feature.workspace.di.WorkspaceModule
import com.georgeci.moneysurfer.navigation.di.NavigationModule
import com.georgeci.moneysurfer.shared.di.SharedModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

// Remote-side modules (RemoteDataModule, SyncImplModule, SyncModule) and the
// online platform bindings live OUTSIDE this graph — host modules (composeApp /
// composeAppOffline) supply them as `extraModules` to `initKoin(...)`. Keeps the
// shared graph Firebase-free so the offline build stays Firebase-free.
@KoinApplication
@Module(
    includes = [
        LocalDataModule::class,
        DomainModule::class,
        SharedModule::class,
        NavigationModule::class,
        SettingsModule::class,
        TransactionModule::class,
        DashboardModule::class,
        LoginModule::class,
        WorkspaceModule::class,
        CategoryModule::class,
        AccountModule::class,
        BudgetModule::class,
        GoalModule::class,
        InsightsModule::class,
    ],
)
@ComponentScan("com.georgeci.moneysurfer")
class AppModule
