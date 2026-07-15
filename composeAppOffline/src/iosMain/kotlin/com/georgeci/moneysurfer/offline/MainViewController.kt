package com.georgeci.moneysurfer.offline

import androidx.compose.ui.window.ComposeUIViewController
import com.georgeci.moneysurfer.App
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.offline.di.offlineWiring
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// Entry point exported by ComposeAppOffline.framework. The Xcode offline
// target/scheme links this framework and calls MainViewController() from its
// SwiftUI App scene.
@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin(isDebug = Platform.isDebugBinary, extraModules = offlineWiring)
    },
) {
    App()
}
