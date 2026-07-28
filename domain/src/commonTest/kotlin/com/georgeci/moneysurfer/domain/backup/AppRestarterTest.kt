package com.georgeci.moneysurfer.domain.backup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The Backup screen only interrupts a finished restore with a "please reopen"
 * dialog when the host says it cannot relaunch itself. Getting the default
 * wrong in either direction is user-visible: `true` by mistake would stop
 * Android and JVM from restarting until the user dismissed a dialog they do
 * not need, `false` on iOS makes a successful restore look like a crash.
 */
class AppRestarterTest : StringSpec({

    "a host that says nothing is assumed to relaunch itself" {
        val restarter = object : AppRestarter {
            override fun restart() = Unit
        }

        restarter.requiresManualRelaunch shouldBe false
    }

    "a host can declare that the user has to reopen the app" {
        val restarter = object : AppRestarter {
            override val requiresManualRelaunch = true
            override fun restart() = Unit
        }

        restarter.requiresManualRelaunch shouldBe true
    }
})
