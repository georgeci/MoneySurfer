package com.georgeci.moneysurfer.sync.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncScopeSpec : StringSpec({

    "toScope maps each reason" {
        SyncReason.APP_START.toScope() shouldBe SyncScope.ActiveWorkspace
        SyncReason.FOREGROUND.toScope() shouldBe SyncScope.ActiveWorkspace
        SyncReason.SWIPE_REFRESH.toScope() shouldBe SyncScope.ActiveWorkspace
        SyncReason.MANUAL.toScope() shouldBe SyncScope.AllUserData
        SyncReason.LOCAL_CHANGE.toScope() shouldBe SyncScope.UploadOnly
        SyncReason.BACKGROUND.toScope() shouldBe SyncScope.ChangedSinceLastSync
    }

    "mergeScope picks widest — AllUserData dominates" {
        mergeScope(SyncScope.UploadOnly, SyncScope.AllUserData) shouldBe SyncScope.AllUserData
        mergeScope(SyncScope.ActiveWorkspace, SyncScope.AllUserData) shouldBe SyncScope.AllUserData
        mergeScope(SyncScope.ChangedSinceLastSync, SyncScope.AllUserData) shouldBe SyncScope.AllUserData
        mergeScope(SyncScope.AllUserData, SyncScope.AllUserData) shouldBe SyncScope.AllUserData
    }

    "mergeScope ActiveWorkspace above ChangedSinceLastSync and UploadOnly" {
        mergeScope(SyncScope.ActiveWorkspace, SyncScope.ChangedSinceLastSync) shouldBe SyncScope.ActiveWorkspace
        mergeScope(SyncScope.UploadOnly, SyncScope.ActiveWorkspace) shouldBe SyncScope.ActiveWorkspace
    }

    "mergeScope ChangedSinceLastSync above UploadOnly" {
        mergeScope(SyncScope.UploadOnly, SyncScope.ChangedSinceLastSync) shouldBe SyncScope.ChangedSinceLastSync
    }

    "mergeScope UploadOnly with UploadOnly stays UploadOnly" {
        mergeScope(SyncScope.UploadOnly, SyncScope.UploadOnly) shouldBe SyncScope.UploadOnly
    }
})
