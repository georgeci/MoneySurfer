package com.georgeci.moneysurfer.data.datastore

import android.os.Build

internal actual val isDynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
