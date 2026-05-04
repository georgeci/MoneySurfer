package com.georgeci.moneysurfer.data.di

import com.georgeci.moneysurfer.domain.repositories.FirebaseConfig
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.georgeci.moneysurfer.data.remote", "com.georgeci.moneysurfer.data.repository")
class RemoteDataModule {

    @Single
    fun firebaseAnalytics(): FirebaseAnalytics = Firebase.analytics

    @Single
    fun firebaseAuth(config: FirebaseConfig): FirebaseAuth = Firebase.auth.also {
        if (config.useEmulator) {
            it.useEmulator(config.emulatorHost, config.authPort)
        }
    }

    @Single
    fun firebaseFirestore(config: FirebaseConfig): FirebaseFirestore = Firebase.firestore.also {
        if (config.useEmulator) {
            it.useEmulator(config.emulatorHost, config.firestorePort)
        }
    }
}
