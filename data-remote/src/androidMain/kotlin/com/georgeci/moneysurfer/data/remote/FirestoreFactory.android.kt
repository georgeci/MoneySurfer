package com.georgeci.moneysurfer.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.dsl.module

val androidRemoteDataModule = module {
    single<FirebaseFirestore> {
        createFirestore(context = get())
    }
}

fun createFirestore(context: Context): FirebaseFirestore {
    FirebaseApp.initializeApp(context)
    check(FirebaseApp.getApps(context).isNotEmpty()) {
        "Firebase not initialized. Check androidApp/google-services.json and Google Services plugin setup."
    }
    return FirebaseFirestore.getInstance()
}
