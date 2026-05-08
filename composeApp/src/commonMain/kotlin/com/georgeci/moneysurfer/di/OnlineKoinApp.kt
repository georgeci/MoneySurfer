package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.data.di.RemoteDataModule
import com.georgeci.moneysurfer.data.di.SyncImplModule
import com.georgeci.moneysurfer.sync.internal.di.SyncModule
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

// koin-compiler generates a `.module` accessor only for `@KoinApplication` classes,
// not for plain `@Module`. We need ONE accessor that pulls in the three remote/sync
// modules so the host can layer them on top of the shared `AppModule` graph at
// runtime, without including them in shared (which must stay Firebase-free).
@KoinApplication
@Module(
    includes = [
        RemoteDataModule::class,
        SyncImplModule::class,
        SyncModule::class,
        OnlineSignInModule::class,
        OnlineTransactionCreationModule::class,
    ],
)
class OnlineKoinApp
