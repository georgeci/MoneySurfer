package com.georgeci.moneysurfer

import androidx.compose.ui.window.ComposeUIViewController
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.di.onlineWiring
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin(isDebug = Platform.isDebugBinary, extraModules = onlineWiring)
    },
) {
    App()
}
