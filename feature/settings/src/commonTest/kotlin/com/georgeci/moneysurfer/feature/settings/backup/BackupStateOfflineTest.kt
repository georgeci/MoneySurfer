package com.georgeci.moneysurfer.feature.settings.backup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Locks in the offline-build gating for the Backup screen. Scheduled cloud
 * backups, "Back up now" and "Delete cloud backup" all target a remote the
 * offline build does not have; the local export/restore pair is served
 * entirely from the on-device database and stays in both variants.
 */
class BackupStateOfflineTest : StringSpec({

    "online state shows the cloud backup surfaces" {
        BackupState(isOffline = false).showCloudBackup shouldBe true
    }

    "offline state hides the cloud backup surfaces" {
        BackupState(isOffline = true).showCloudBackup shouldBe false
    }
})
