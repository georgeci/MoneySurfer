package com.georgeci.moneysurfer.feature.settings.backup

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.backup.BackupExporter
import com.georgeci.moneysurfer.domain.backup.BackupImporter
import com.georgeci.moneysurfer.domain.backup.BackupManifest
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.BufferedSink
import okio.BufferedSource

/**
 * The Backup screen is local-only: every event it can raise has to end in a
 * real state change or a real side effect. Cloud scheduling, "Back up now" and
 * "Delete cloud backup" used to sit here posting a `NotImplemented` effect the
 * screen dropped on the floor, so taps — including a destructive confirmation —
 * silently did nothing. These tests pin the surviving surface.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun viewModel(isOffline: Boolean = false) = BackupViewModel(
        exporter = NoopExporter,
        importer = NoopImporter,
        clock = ClockUseCase(),
        hostCapabilities = FakeHostCapabilities(isOffline = isOffline),
    )

    "download opens the export options, and confirming asks for a save target" {
        runTest {
            val vm = viewModel()

            vm.onEvent(BackupEvent.OnDownloadClick)
            vm.currentState.showExportOptions shouldBe true

            vm.sideEffects.effectFlow.test {
                vm.onEvent(BackupEvent.OnExportOptionsConfirmed(passphrase = null))
                awaitItem().shouldBeInstanceOf<BackupEffect.RequestSaveFile>()
            }
            vm.currentState.showExportOptions shouldBe false
        }
    }

    "dismissing the export options asks for nothing" {
        runTest {
            val vm = viewModel()
            vm.onEvent(BackupEvent.OnDownloadClick)

            vm.onEvent(BackupEvent.OnExportOptionsDismissed)

            vm.currentState.showExportOptions shouldBe false
        }
    }

    "restore confirms first, then asks for a file to open" {
        runTest {
            val vm = viewModel()

            vm.onEvent(BackupEvent.OnRestoreClick)
            vm.currentState.showRestoreConfirmation shouldBe true

            vm.sideEffects.effectFlow.test {
                vm.onEvent(BackupEvent.OnRestoreConfirmed)
                awaitItem() shouldBe BackupEffect.RequestOpenFile
            }
            vm.currentState.showRestoreConfirmation shouldBe false
        }
    }

    "dismissing the restore confirmation leaves the data alone" {
        runTest {
            val vm = viewModel()
            vm.onEvent(BackupEvent.OnRestoreClick)

            vm.onEvent(BackupEvent.OnRestoreDismissed)

            vm.currentState.showRestoreConfirmation shouldBe false
        }
    }

    "a cancelled save picker is not reported as a failure" {
        runTest {
            val vm = viewModel()
            vm.onEvent(BackupEvent.OnDownloadClick)
            vm.onEvent(BackupEvent.OnExportOptionsConfirmed(passphrase = null))

            vm.onEvent(BackupEvent.OnSaveSinkChosen(sink = null))

            vm.currentState.phase shouldBe BackupPhase.Idle
        }
    }

    "the offline build is carried into the state so the hero can say so" {
        runTest {
            viewModel(isOffline = true).currentState.isOffline shouldBe true
            viewModel(isOffline = false).currentState.isOffline shouldBe false
        }
    }
})

private object NoopExporter : BackupExporter {
    override suspend fun exportTo(sink: BufferedSink, passphrase: String?): Result<BackupManifest> =
        error("no export in these tests")
}

private object NoopImporter : BackupImporter {
    override suspend fun isPassphraseProtected(source: BufferedSource): Boolean = false

    override suspend fun importFrom(source: BufferedSource, passphrase: String?): Result<BackupManifest> =
        error("no import in these tests")
}

private class FakeHostCapabilities(override val isOffline: Boolean) : HostCapabilities {
    override val signInEmailPassword: Boolean = !isOffline
    override val signInAnonymous: Boolean = !isOffline
    override val signInDemo: Boolean = false
    override val transferEnabled: Boolean = true
    override val dashboardWidgetStyle: Boolean = false
}
