package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.remote.AppConfigDoc
import com.georgeci.moneysurfer.data.remote.AppConfigRemoteSource
import com.georgeci.moneysurfer.domain.model.RemoteAppConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

private fun repository(source: AppConfigRemoteSource) = AppConfigRepositoryImpl(source)

/**
 * Everything here is about the gate failing *open*. `AppVersionGateImpl` reads a null as
 * "supported", so a repository that let an exception out — or turned an unpublished document into
 * a zeroed config — would lock working installs out of their own local data the first time
 * Firestore was unreachable.
 */
class AppConfigRepositoryImplTest : StringSpec({

    "a published document is mapped field for field" {
        runTest {
            val repository = repository {
                AppConfigDoc(
                    minSupportedAppVersionCode = 10,
                    latestAppVersionCode = 42,
                    forceUpdate = true,
                    message = "Please update",
                )
            }

            repository.fetch() shouldBe RemoteAppConfig(
                minSupportedAppVersionCode = 10,
                latestAppVersionCode = 42,
                forceUpdate = true,
                message = "Please update",
            )
        }
    }

    "a gate document that was never published reads as no config" {
        runTest {
            repository { null }.fetch().shouldBeNull()
        }
    }

    // Offline, a rules change, a transport hiccup: all the same answer, and none of them may
    // escape to the caller.
    "an unreachable server reads as no config rather than throwing" {
        runTest {
            repository { error("PERMISSION_DENIED") }.fetch().shouldBeNull()
        }
    }

    "a document with only defaults still maps, so the gate can decide it means nothing" {
        runTest {
            repository { AppConfigDoc() }.fetch() shouldBe RemoteAppConfig(
                minSupportedAppVersionCode = 0,
                latestAppVersionCode = 0,
                forceUpdate = false,
                message = null,
            )
        }
    }
})
