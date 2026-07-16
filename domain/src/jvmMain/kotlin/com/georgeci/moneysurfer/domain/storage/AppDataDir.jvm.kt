package com.georgeci.moneysurfer.domain.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Locale

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
 * `APPDATA` / `XDG_DATA_HOME` are honoured only when set to an absolute path, so
 * a stray relative value cannot redirect storage into the working directory. The
 * directory is created with owner-only permissions (0700) where the file system
 * supports POSIX permissions, so local databases and preferences are not left
 * world-readable under a shared /tmp. Creation or permission-tightening failures
 * are surfaced rather than swallowed.
 */
fun appDataDir(): File {
    val home = File(System.getProperty("user.home").orEmpty())
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    val base = when {
        os.contains("mac") || os.contains("darwin") ->
            File(home, "Library/Application Support")
        os.contains("win") ->
            absoluteEnvDir("APPDATA") ?: File(home, "AppData/Roaming")
        else ->
            absoluteEnvDir("XDG_DATA_HOME") ?: File(home, ".local/share")
    }
    return File(base, APP_DIR_NAME).also { it.createOwnerOnly() }
}

/** The environment variable as a directory, but only when it is an absolute path. */
private fun absoluteEnvDir(name: String): File? =
    System.getenv(name)
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isAbsolute }

private fun File.createOwnerOnly() {
    val path: Path = toPath()
    if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
        val perms = PosixFilePermissions.fromString(OWNER_ONLY)
        Files.createDirectories(path, PosixFilePermissions.asFileAttribute(perms))
        // Enforce owner-only even if the directory already existed with looser perms.
        Files.setPosixFilePermissions(path, perms)
    } else if (!mkdirs() && !isDirectory) {
        throw IOException("Unable to create app-data directory: $path")
    }
}
