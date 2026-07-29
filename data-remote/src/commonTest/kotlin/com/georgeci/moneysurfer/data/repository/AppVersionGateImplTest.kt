package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.model.RemoteAppConfig
import com.georgeci.moneysurfer.domain.repositories.AppConfigRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest

private const val THIS_BUILD = 100

private fun remoteConfig(
    minSupported: Int = 1,
    latest: Int = THIS_BUILD,
    forceUpdate: Boolean = false,
    message: String? = null,
) = RemoteAppConfig(
    minSupportedAppVersionCode = minSupported,
    latestAppVersionCode = latest,
    forceUpdate = forceUpdate,
    message = message,
)

private fun gate(config: RemoteAppConfig?, versionCode: Int = THIS_BUILD) = AppVersionGateImpl(
    appConfigRepository = object : AppConfigRepository {
        override suspend fun fetch(): RemoteAppConfig? = config
    },
    appInfo = AppInfo(version = "1.0.0", versionCode = versionCode),
)

/**
 * The kill switch for builds the server no longer accepts. Both directions are load-bearing: an
 * over-eager gate locks working installs out of their own local data, and a gate that fails open
 * on an unreadable config lets a build the server has stopped supporting keep writing to it.
 */
class AppVersionGateImplTest : StringSpec({

    "nothing is decided until the first refresh" {
        gate(remoteConfig()).status.value.shouldBeNull()
    }

    // The gate speaks for the server, and an unreachable server has not said "no".
    "a config that could not be read leaves the build supported" {
        runTest {
            val gate = gate(config = null)

            gate.refresh() shouldBe AppVersionStatus.Supported
            gate.status.value shouldBe AppVersionStatus.Supported
        }
    }

    "a build at or above the floor with nothing newer published is supported" {
        runTest {
            gate(remoteConfig(minSupported = 50, latest = THIS_BUILD)).refresh() shouldBe
                AppVersionStatus.Supported
        }
    }

    "a build below the minimum supported version is cut off" {
        runTest {
            val status = gate(remoteConfig(minSupported = THIS_BUILD + 1)).refresh()

            status shouldBe AppVersionStatus.Unsupported(
                message = "This app version is no longer supported. Please update.",
            )
        }
    }

    "the server's own wording is used when it sends one" {
        runTest {
            val status = gate(
                remoteConfig(minSupported = THIS_BUILD + 1, message = "Update from the store."),
            ).refresh()

            status shouldBe AppVersionStatus.Unsupported(message = "Update from the store.")
        }
    }

    // The escape hatch for a build that is broken rather than merely old.
    "forceUpdate cuts off a build that is otherwise current" {
        runTest {
            gate(remoteConfig(minSupported = 1, forceUpdate = true)).refresh()
                .shouldBeInstanceOf<AppVersionStatus.Unsupported>()
        }
    }

    "a newer published build is offered, not enforced" {
        runTest {
            gate(remoteConfig(latest = THIS_BUILD + 1, message = "New version available"))
                .refresh() shouldBe AppVersionStatus.UpdateAvailable("New version available")
        }
    }

    "sync is allowed before the first refresh, so a cold start is never blocked on the network" {
        gate(remoteConfig()).isSyncAllowed() shouldBe true
    }

    "sync stays allowed while an update is merely available" {
        runTest {
            val gate = gate(remoteConfig(latest = THIS_BUILD + 1))
            gate.refresh()

            gate.isSyncAllowed() shouldBe true
        }
    }

    // An unsupported build's writes are the reason the floor exists — they are the ones that could
    // put a shape the server no longer understands into a shared workspace.
    "sync is refused once the build is unsupported" {
        runTest {
            val gate = gate(remoteConfig(minSupported = THIS_BUILD + 1))
            gate.refresh()

            gate.isSyncAllowed() shouldBe false
        }
    }
})
