package com.georgeci.moneysurfer.data.di

import kotlinx.serialization.json.Json
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.georgeci.moneysurfer.data.sync")
class SyncImplModule {

    @Single
    fun syncJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
