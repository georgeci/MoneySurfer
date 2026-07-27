package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
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
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * `UIDocumentPicker` is asymmetric with the cross-platform contract: it moves or
 * copies files that already exist on disk, and it never hands back a stream. So
 * both directions go through the app's temp directory.
 *
 *  - **Save** — we open a sink onto a temp file straight away and hand it to the
 *    caller. The archive is written there first; closing the sink is what tells
 *    us the export finished, and only then do we present the "export" picker so
 *    the user can place the finished file. The temp copy is deleted once the
 *    picker is done with it.
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
        onPicked = { urls -> onDocumentsPicked(urls) },
        onCancelled = { onPickerCancelled() },
    )

    /** Temp directories staged for this launcher, deleted on dispose if still around. */
    private val stagedDirectories = mutableSetOf<String>()

    /**
     * Which picker the next delegate callback belongs to. An export is queued the
     * moment its sink closes, so the two modes cannot share a single flag: a late
     * export could otherwise claim the callback of an open picker already on screen.
     */
    private var mode = PickerMode.None

    /** Set once the composable leaves; nothing may be presented afterwards. */
    private var disposed = false

    override fun launchSave(suggestedName: String) {
        val stagedFile = if (disposed) null else stageTempFile(suggestedName)
        if (stagedFile == null) {
            onSavePicked(null)
            return
        }
        val sink = OnCloseSink(FileSystem.SYSTEM.sink(stagedFile)) {
            onMainQueue { presentExportPicker(stagedFile) }
        }
        onSavePicked(sink.buffer())
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
        clearStagedFiles()
    }

    /**
     * Presents the finished archive for the user to place. Skipped when nothing was
     * written — a cancelled export closes its sink while `viewModelScope` unwinds,
     * and a save sheet for an empty file (on a screen the user may already have
     * left) is pure noise.
     *
     * A *failed* export is not distinguishable here: it also closes a sink that has
     * bytes in it, so the user can be offered a truncated archive alongside the
     * error snackbar. Telling the two apart needs a success signal on the shared
     * picker contract, which this change deliberately leaves alone.
     */
    private fun presentExportPicker(stagedFile: Path) {
        if (disposed || mode == PickerMode.Open) {
            deleteStagedDirectory(stagedFile.parent?.toString())
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
            return
        }
        val path = url?.path
        if (path == null) {
            onOpenPicked(null)
            return
        }
        // `asCopy` puts the copy in our own temp area, so we own it and delete it
        // once the importer is done reading.
        val file = path.toPath()
        val source = OnCloseSource(FileSystem.SYSTEM.source(file)) {
            runCatching { FileSystem.SYSTEM.delete(file) }
        }
        onOpenPicked(source.buffer())
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

/**
 * Maps the shared MIME types onto uniform type identifiers, which is what the
 * iOS 14+ picker takes. Anything the system cannot resolve is dropped, and an
 * empty result falls back to "any file" rather than a picker that shows nothing.
 */
private fun BackupPickerFormat.contentTypes(): List<UTType> =
    openMimeTypes.mapNotNull { UTType.typeWithMIMEType(it) }.ifEmpty { listOf(UTTypeData) }

/** Which picker, if any, the next delegate callback belongs to. */
private enum class PickerMode { None, Export, Open }

/**
 * Runs [onClose] the first time the stream is closed. `RealBufferedSink` already
 * short-circuits its own second close, but the export error path closes the sink
 * again by hand, so the flag keeps "present the picker once" true regardless of
 * how the caller unwinds.
 */
private class OnCloseSink(
    private val delegate: Sink,
    private val onClose: () -> Unit,
) : Sink by delegate {
    private var closed = false

    override fun close() {
        delegate.close()
        if (!closed) {
            closed = true
            onClose()
        }
    }
}

/** [OnCloseSink]'s read-side twin. */
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

/** Export finishes on a background dispatcher; UIKit presentation must not. */
private fun onMainQueue(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}
