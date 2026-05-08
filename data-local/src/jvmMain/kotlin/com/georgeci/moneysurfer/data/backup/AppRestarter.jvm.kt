package com.georgeci.moneysurfer.data.backup

import kotlin.system.exitProcess

actual class AppRestarter {
    /**
     * Best-effort restart for desktop. Spawns a new JVM with the same command
     * line, then exits. If the command isn't recoverable we just exit and rely
     * on the user to relaunch.
     */
    actual fun restart() {
        runCatching {
            val handle = ProcessHandle.current()
            val info = handle.info()
            val command = info.command().orElse(null) ?: return@runCatching
            val args = info.arguments().orElse(emptyArray())
            ProcessBuilder(listOf(command) + args.toList())
                .inheritIO()
                .start()
        }
        exitProcess(0)
    }
}
