package com.georgeci.moneysurfer.sync.network

import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.internal.network.NetworkWaitMode
import com.georgeci.moneysurfer.sync.internal.network.toNetworkWaitMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NetworkWaitModeSpec : StringSpec({

    "reason mapping for user-initiated reasons is Indefinite" {
        SyncReason.MANUAL.toNetworkWaitMode() shouldBe NetworkWaitMode.Indefinite
        SyncReason.SWIPE_REFRESH.toNetworkWaitMode() shouldBe NetworkWaitMode.Indefinite
        SyncReason.LOCAL_CHANGE.toNetworkWaitMode() shouldBe NetworkWaitMode.Indefinite
        SyncReason.FOREGROUND.toNetworkWaitMode() shouldBe NetworkWaitMode.Indefinite
    }

    "reason mapping for APP_START is Bounded(30s)" {
        val mode = SyncReason.APP_START.toNetworkWaitMode().shouldBeInstanceOf<NetworkWaitMode.Bounded>()
        mode.timeout shouldBe 30.seconds
    }

    "reason mapping for BACKGROUND is Bounded(5m)" {
        val mode = SyncReason.BACKGROUND.toNetworkWaitMode().shouldBeInstanceOf<NetworkWaitMode.Bounded>()
        mode.timeout shouldBe 5.minutes
    }

    "set merge picks smallest bounded timeout" {
        val merged = setOf(SyncReason.APP_START, SyncReason.BACKGROUND).toNetworkWaitMode()
            .shouldBeInstanceOf<NetworkWaitMode.Bounded>()
        merged.timeout shouldBe 30.seconds
    }

    "set merge with Indefinite and Bounded picks Bounded" {
        // Bounded is stricter than Indefinite — merging must not extend a background
        // request's wait window by including a manual reason.
        val merged = setOf(SyncReason.MANUAL, SyncReason.BACKGROUND).toNetworkWaitMode()
            .shouldBeInstanceOf<NetworkWaitMode.Bounded>()
        merged.timeout shouldBe 5.minutes
    }

    "set merge with only Indefinite reasons stays Indefinite" {
        setOf(SyncReason.MANUAL, SyncReason.LOCAL_CHANGE).toNetworkWaitMode() shouldBe NetworkWaitMode.Indefinite
    }

    "empty set is Indefinite" {
        emptySet<SyncReason>().toNetworkWaitMode() shouldBe NetworkWaitMode.Indefinite
    }
})
