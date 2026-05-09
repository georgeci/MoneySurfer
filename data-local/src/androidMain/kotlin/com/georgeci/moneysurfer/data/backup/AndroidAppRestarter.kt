package com.georgeci.moneysurfer.data.backup

import android.content.Context
import android.content.Intent
import com.georgeci.moneysurfer.domain.backup.AppRestarter
import kotlin.system.exitProcess

class AndroidAppRestarter(private val context: Context) : AppRestarter {
    override fun restart() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launchIntent)
        }
        exitProcess(0)
    }
}
