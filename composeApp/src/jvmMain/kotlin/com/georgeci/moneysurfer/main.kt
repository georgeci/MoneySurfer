@file:Suppress("detekt.Filename")

package com.georgeci.moneysurfer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.georgeci.moneysurfer.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MoneySurfer",
        ) {
            App()
        }
    }
}
