package com.georgeci.moneysurfer.data.backup

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

actual class AppRestarter(private val context: Context) {
    actual fun restart() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launchIntent)
        }
        exitProcess(0)
    }
}
