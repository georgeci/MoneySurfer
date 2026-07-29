package com.georgeci.moneysurfer.data.sync.plugin

import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.repository.ConflictResolution
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class ApplyResolutionSpec : StringSpec({

    "take remote writes the remote value without reporting a conflict" {
        val writes = mutableListOf<String>()

        val result = applyResolution(ConflictResolution.TakeRemote("remote"), writes::add)

        writes shouldBe listOf("remote")
        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
    }

    "merged writes the merged value and reports a conflict" {
        val writes = mutableListOf<String>()

        val result = applyResolution(ConflictResolution.Merged("merged"), writes::add)

        writes shouldBe listOf("merged")
        result shouldBe EntityApplyResult(applied = true, wasConflict = true)
    }

    "take local neither writes nor reports the remote value as applied" {
        val writes = mutableListOf<String>()

        val result = applyResolution(ConflictResolution.TakeLocal("local"), writes::add)

        writes.shouldBeEmpty()
        result shouldBe EntityApplyResult(applied = false, wasConflict = true)
    }

    "skip neither writes nor reports the remote value as applied" {
        val writes = mutableListOf<String>()

        val result = applyResolution(ConflictResolution.Skip, writes::add)

        writes.shouldBeEmpty()
        result shouldBe EntityApplyResult(applied = false, wasConflict = true)
    }
})
