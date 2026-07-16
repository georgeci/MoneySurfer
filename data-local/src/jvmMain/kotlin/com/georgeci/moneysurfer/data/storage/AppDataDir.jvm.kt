package com.georgeci.moneysurfer.data.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

private const val APP_DIR_NAME = "MoneySurfer"
private const val OWNER_ONLY = "rwx------"

/**
 * Per-user application-data directory for MoneySurfer on JVM desktop targets.
 *
 * Resolves to the platform convention:
 * - macOS:   ~/Library/Application Support/MoneySurfer
 * - Windows: %APPDATA%\MoneySurfer (falling back to ~/AppData/Roaming)
 * - Linux:   $XDG_DATA_HOME/MoneySurfer (falling back to ~/.local/share)
 *
 * The directory is created with owner-only permissions (0700) where the file
 * system supports POSIX permissions, so local databases and preferences are not
 * left world-readable under a shared /tmp.
 */
internal fun appDataDir(): File {
    val home = System.getProperty("user.home").orEmpty()
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val base = when {
        os.contains("mac") || os.contains("darwin") ->
            File(home, "Library/Application Support")
        os.contains("win") ->
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(home, "AppData/Roaming")
        else ->
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(home, ".local/share")
    }
    return File(base, APP_DIR_NAME).also { it.createOwnerOnly() }
}

private fun File.createOwnerOnly() {
    val path: Path = toPath()
    val posix = path.fileSystem.supportedFileAttributeViews().contains("posix")
    if (posix) {
        val perms = PosixFilePermissions.fromString(OWNER_ONLY)
        runCatching {
            Files.createDirectories(path, PosixFilePermissions.asFileAttribute(perms))
        }.onFailure { mkdirs() }
        // Tighten an already-existing directory as well.
        runCatching { Files.setPosixFilePermissions(path, perms) }
    } else {
        mkdirs()
    }
}
