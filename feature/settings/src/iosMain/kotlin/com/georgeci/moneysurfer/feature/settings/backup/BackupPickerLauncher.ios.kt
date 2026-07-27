package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import co.touchlab.kermit.Logger
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

/**
 * `UIDocumentPicker` is asymmetric with the cross-platform contract: it moves or
 * copies files that already exist on disk, and it never hands back a stream. So
 * both directions go through the app's temp directory.
 *
 *  - **Save** — the sink handed to the caller writes into a staged temp file.
 *    The "where do I put it" picker is presented from
 *    [BackupPickerLauncher.onSaveCompleted], not from closing the sink: an
 *    aborted export closes its sink too, and presenting on close would offer
 *    the user a truncated archive. A staged file nobody reports on is deleted
 *    when the screen goes away.
 *  - **Open** — the picker runs in `asCopy` mode, so iOS drops a copy we own into
 *    our temp directory and we never touch a security-scoped URL. The source we
 *    return deletes that copy when it is closed.
 *
 * The delegate is held by the launcher because `UIDocumentPickerViewController`
 * keeps only a weak reference to it.
 */
@Composable
actual fun rememberBackupPickerLauncher(
    format: BackupPickerFormat,
    onSavePicked: (BufferedSink?) -> Unit,
    onOpenPicked: (BufferedSource?) -> Unit,
): BackupPickerLauncher {
    val host = LocalUIViewController.current
    // The callbacks are fresh lambdas on every recomposition. Reading them through
    // a state keeps the launcher — and with it the strongly-held delegate — alive
    // across a recomposition that happens mid-pick.
    val currentOnSavePicked by rememberUpdatedState(onSavePicked)
    val currentOnOpenPicked by rememberUpdatedState(onOpenPicked)

    val launcher = remember(host, format) {
        IosBackupPickerLauncher(
            host = host,
            format = format,
            onSavePicked = { currentOnSavePicked(it) },
            onOpenPicked = { currentOnOpenPicked(it) },
        )
    }
    DisposableEffect(launcher) {
        onDispose { launcher.dispose() }
    }
    return launcher
}

private class IosBackupPickerLauncher(
    private val host: UIViewController,
    private val format: BackupPickerFormat,
    private val onSavePicked: (BufferedSink?) -> Unit,
    private val onOpenPicked: (BufferedSource?) -> Unit,
) : BackupPickerLauncher {

    /** Strongly held: the picker's `delegate` property is weak. */
    private val delegate = PickerDelegate(
        onPicked = { urls -> guarded { onDocumentsPicked(urls) } },
        onCancelled = { guarded { onPickerCancelled() } },
    )

    /** Temp directories staged for this launcher, deleted on dispose if still around. */
    private val stagedDirectories = mutableSetOf<String>()

    /** Which picker, if any, the next delegate callback belongs to. */
    private var mode = PickerMode.None

    /** Archive being written by the last [launchSave], until [onSaveCompleted] reports on it. */
    private var stagedExport: Path? = null

    /** A finished archive that could not be presented because a picker was already up. */
    private var deferredExport: Path? = null

    /** Set once the composable leaves; nothing may be presented afterwards. */
    private var disposed = false

    override fun launchSave(suggestedName: String) {
        discardStagedExport()
        val staged = if (disposed) null else stageTempFile(suggestedName)
        val sink = staged?.let { runCatching { FileSystem.SYSTEM.sink(it) }.getOrNull() }
        if (sink == null) {
            if (staged != null) deleteStagedDirectory(staged.parent?.toString())
            onSavePicked(null)
            return
        }
        stagedExport = staged
        onSavePicked(sink.buffer())
    }

    override fun onSaveCompleted(succeeded: Boolean) {
        val staged = stagedExport ?: return
        stagedExport = null
        if (succeeded) presentExportPicker(staged) else deleteStagedDirectory(staged.parent?.toString())
    }

    override fun launchOpen() {
        if (disposed) return
        mode = PickerMode.Open
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = format.contentTypes(),
            asCopy = true,
        )
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }

    fun dispose() {
        disposed = true
        mode = PickerMode.None
        stagedExport = null
        deferredExport = null
        clearStagedFiles()
    }

    /**
     * Presents the finished archive so the user can place it. An empty file means
     * the export was cancelled before it wrote anything, which is nothing to offer.
     * A picker already on screen wins; this one is retried once that picker closes.
     */
    private fun presentExportPicker(stagedFile: Path) {
        if (disposed) {
            deleteStagedDirectory(stagedFile.parent?.toString())
            return
        }
        if (mode != PickerMode.None) {
            deferredExport = stagedFile
            return
        }
        val size = FileSystem.SYSTEM.metadataOrNull(stagedFile)?.size
        if (size == null || size == 0L) {
            deleteStagedDirectory(stagedFile.parent?.toString())
            return
        }
        mode = PickerMode.Export
        val picker = UIDocumentPickerViewController(
            forExportingURLs = listOf(NSURL.fileURLWithPath(stagedFile.toString())),
            asCopy = true,
        )
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }

    private fun onDocumentsPicked(urls: List<NSURL>) {
        val url = urls.firstOrNull()
        val finishedMode = mode
        mode = PickerMode.None
        if (finishedMode != PickerMode.Open) {
            // `asCopy` left our staged file behind once iOS finished copying it out.
            clearStagedFiles()
            presentDeferredExport()
            return
        }
        // `asCopy` puts the copy in our own temp area, so we own it and delete it
        // once the importer is done reading.
        val file = url?.path?.toPath()
        val source = file?.let { runCatching { FileSystem.SYSTEM.source(it) }.getOrNull() }
        if (file == null || source == null) {
            onOpenPicked(null)
        } else {
            onOpenPicked(OnCloseSource(source) { runCatching { FileSystem.SYSTEM.delete(file) } }.buffer())
        }
        presentDeferredExport()
    }

    private fun onPickerCancelled() {
        val cancelledMode = mode
        mode = PickerMode.None
        if (cancelledMode == PickerMode.Open) {
            onOpenPicked(null)
        } else {
            // The archive was written but the user declined to place it anywhere.
            clearStagedFiles()
        }
        presentDeferredExport()
    }

    private fun presentDeferredExport() {
        val deferred = deferredExport ?: return
        deferredExport = null
        presentExportPicker(deferred)
    }

    /** Drops a staged archive nobody reported on — a previous export that never completed. */
    private fun discardStagedExport() {
        val abandoned = stagedExport ?: return
        stagedExport = null
        deleteStagedDirectory(abandoned.parent?.toString())
    }

    /**
     * Stages [name] inside a throwaway directory so the picker shows the suggested
     * filename verbatim and two exports in a row cannot collide.
     */
    private fun stageTempFile(name: String): Path? {
        val directory = NSTemporaryDirectory().toPath() / NSUUID().UUIDString()
        return runCatching {
            FileSystem.SYSTEM.createDirectories(directory)
            stagedDirectories += directory.toString()
            directory / name
        }.getOrNull()
    }

    private fun clearStagedFiles() {
        stagedDirectories.toList().forEach { deleteStagedDirectory(it) }
    }

    private fun deleteStagedDirectory(directory: String?) {
        if (directory == null) return
        stagedDirectories -= directory
        runCatching { FileSystem.SYSTEM.deleteRecursively(directory.toPath()) }
    }
}

/** Which picker, if any, the next delegate callback belongs to. */
private enum class PickerMode { None, Export, Open }

/**
 * Maps the shared MIME types onto uniform type identifiers, which is what the
 * iOS 14+ picker takes. Anything the system cannot resolve is dropped, and an
 * empty result falls back to "any file" rather than a picker that shows nothing.
 */
private fun BackupPickerFormat.contentTypes(): List<UTType> =
    openMimeTypes.mapNotNull { UTType.typeWithMIMEType(it) }.ifEmpty { listOf(UTTypeData) }

/** Deletes the picker's temp copy once the importer is done with it. */
private class OnCloseSource(
    private val delegate: Source,
    private val onClose: () -> Unit,
) : Source by delegate {
    private var closed = false

    override fun close() {
        delegate.close()
        if (!closed) {
            closed = true
            onClose()
        }
    }
}

private class PickerDelegate(
    private val onPicked: (List<NSURL>) -> Unit,
    private val onCancelled: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancelled()
    }
}

/**
 * UIKit invokes the delegate directly, and a Kotlin exception escaping a method
 * that Objective-C called terminates the process instead of unwinding. Nothing
 * reachable from a picker callback may throw past this point.
 */
private inline fun guarded(block: () -> Unit) {
    runCatching(block).onFailure { error ->
        Logger.e(error) { "Backup picker callback failed" }
    }
}
