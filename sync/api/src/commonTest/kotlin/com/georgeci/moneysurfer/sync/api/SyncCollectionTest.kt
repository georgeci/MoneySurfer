package com.georgeci.moneysurfer.sync.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the wire values of [SyncCollection].
 *
 * These strings are not cosmetic names. Each one is used twice: as a Firestore path segment,
 * and — via [com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin.firestoreCollectionName] —
 * as the `collection` half of the `sync_meta` primary key, where it is persisted in the local
 * database as a pull cursor. Renaming a constant's VALUE therefore orphans every stored cursor:
 * the next pull sees no cursor, restarts from epoch zero and re-downloads the whole collection
 * on every client.
 *
 * Renaming a constant's Kotlin identifier is safe and the compiler will catch the fallout.
 * Changing the string it holds is not, which is what this test guards.
 */
class SyncCollectionTest : StringSpec({

    "collection wire values are stable — they are persisted sync_meta cursor keys" {
        SyncCollection.WORKSPACE_MEMBERS shouldBe "members"
        SyncCollection.WORKSPACE_INVITES shouldBe "invites"
        SyncCollection.ACCOUNTS shouldBe "accounts"
        SyncCollection.CATEGORIES shouldBe "categories"
        SyncCollection.TRANSACTIONS shouldBe "transactions"
        SyncCollection.BUDGETS shouldBe "budgets"
        SyncCollection.RECURRING_RULES shouldBe "recurringRules"
        SyncCollection.GOALS shouldBe "goals"
        SyncCollection.GOAL_CONTRIBUTIONS shouldBe "goalContributions"
    }
})
