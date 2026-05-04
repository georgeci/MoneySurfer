package com.georgeci.moneysurfer.domain.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Locks in the relative order of [SyncStep] entries.
 *
 * `WorkspaceSyncRepositoryImpl.syncWorkspace` emits these in declaration order.
 * Firestore rules gate sub-collection writes on `isMember(wid)`, which reads
 * `/workspaces/{wid}/members/{uid}`. That row MUST be written before any
 * other sub-collection write — otherwise everything after `PUSH_WORKSPACE`
 * trips PERMISSION_DENIED.
 *
 * Invites sit between members and the data sub-collections — they need the member
 * row in place (rules read `isMember`/`isOwner`) and bringing them up before
 * accounts/categories keeps the invite roster fresh for the UI banner.
 *
 * If a future refactor reorders the enum (e.g. moves `PUSH_MEMBERS` after
 * `PUSH_ACCOUNTS`) this test surfaces the regression even without a
 * Firestore emulator.
 */
class SyncStepOrderTest : StringSpec({

    "PUSH_MEMBERS sits between PUSH_WORKSPACE and PUSH_INVITES" {
        val push = SyncStep.PUSH_MEMBERS.ordinal
        (push > SyncStep.PUSH_WORKSPACE.ordinal).shouldBeTrue()
        (push < SyncStep.PUSH_INVITES.ordinal).shouldBeTrue()
    }

    "PUSH_INVITES sits between PUSH_MEMBERS and PUSH_ACCOUNTS" {
        (SyncStep.PUSH_INVITES.ordinal > SyncStep.PUSH_MEMBERS.ordinal).shouldBeTrue()
        (SyncStep.PUSH_INVITES.ordinal < SyncStep.PUSH_ACCOUNTS.ordinal).shouldBeTrue()
    }

    "PUSH order is workspace → members → invites → accounts → categories → transactions" {
        val expected = listOf(
            SyncStep.PUSH_WORKSPACE,
            SyncStep.PUSH_MEMBERS,
            SyncStep.PUSH_INVITES,
            SyncStep.PUSH_ACCOUNTS,
            SyncStep.PUSH_CATEGORIES,
            SyncStep.PUSH_TRANSACTIONS,
        )
        expected.zipWithNext().forEach { (a, b) ->
            (a.ordinal < b.ordinal).shouldBeTrue()
        }
    }

    "PULL_MEMBERS sits between PULL_WORKSPACE and PULL_INVITES" {
        (SyncStep.PULL_MEMBERS.ordinal > SyncStep.PULL_WORKSPACE.ordinal).shouldBeTrue()
        (SyncStep.PULL_MEMBERS.ordinal < SyncStep.PULL_INVITES.ordinal).shouldBeTrue()
    }

    "PULL_INVITES sits between PULL_MEMBERS and PULL_ACCOUNTS" {
        (SyncStep.PULL_INVITES.ordinal > SyncStep.PULL_MEMBERS.ordinal).shouldBeTrue()
        (SyncStep.PULL_INVITES.ordinal < SyncStep.PULL_ACCOUNTS.ordinal).shouldBeTrue()
    }

    "PULL order is workspace → members → invites → accounts → categories → transactions" {
        val expected = listOf(
            SyncStep.PULL_WORKSPACE,
            SyncStep.PULL_MEMBERS,
            SyncStep.PULL_INVITES,
            SyncStep.PULL_ACCOUNTS,
            SyncStep.PULL_CATEGORIES,
            SyncStep.PULL_TRANSACTIONS,
        )
        expected.zipWithNext().forEach { (a, b) ->
            (a.ordinal < b.ordinal).shouldBeTrue()
        }
    }

    "all PUSH steps precede all PULL steps" {
        val pushes = listOf(
            SyncStep.PUSH_WORKSPACE,
            SyncStep.PUSH_MEMBERS,
            SyncStep.PUSH_INVITES,
            SyncStep.PUSH_ACCOUNTS,
            SyncStep.PUSH_CATEGORIES,
            SyncStep.PUSH_TRANSACTIONS,
        )
        val pulls = listOf(
            SyncStep.PULL_WORKSPACE,
            SyncStep.PULL_MEMBERS,
            SyncStep.PULL_INVITES,
            SyncStep.PULL_ACCOUNTS,
            SyncStep.PULL_CATEGORIES,
            SyncStep.PULL_TRANSACTIONS,
        )
        val maxPush = pushes.maxOf { it.ordinal }
        val minPull = pulls.minOf { it.ordinal }

        (maxPush < minPull).shouldBeTrue()
    }

    "exhaustive enumeration — no orphan steps" {
        // If a step is added in the future, this set membership check forces an update so we
        // remember to think about its position. The complete set is asserted below.
        SyncStep.entries.toSet() shouldBe setOf(
            SyncStep.PUSH_WORKSPACE,
            SyncStep.PUSH_MEMBERS,
            SyncStep.PUSH_INVITES,
            SyncStep.PUSH_ACCOUNTS,
            SyncStep.PUSH_CATEGORIES,
            SyncStep.PUSH_TRANSACTIONS,
            SyncStep.PULL_WORKSPACE,
            SyncStep.PULL_MEMBERS,
            SyncStep.PULL_INVITES,
            SyncStep.PULL_ACCOUNTS,
            SyncStep.PULL_CATEGORIES,
            SyncStep.PULL_TRANSACTIONS,
        )
    }
})
