package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.remote.GoalContributionDoc
import com.georgeci.moneysurfer.data.remote.GoalDoc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val WORKSPACE = "ws-1"

class GoalDtoMapperSpec : StringSpec({

    "a goal round-trips through the wire format" {
        val entity = GoalDoc(
            title = "New bike",
            emoji = "🚲",
            hue = 210,
            target = 150_000,
            currencyCode = "USD",
            startDate = "2026-07-01",
            targetDate = "2026-12-31",
            accountId = "acc-1",
            status = "PAUSED",
            createdAt = 1,
            updatedAt = 2,
        ).toEntity(id = "goal-1", workspaceId = WORKSPACE)

        entity.id shouldBe "goal-1"
        entity.workspaceId shouldBe WORKSPACE
        entity.toDoc().copy(clientVersionCode = 1) shouldBe GoalDoc(
            title = "New bike",
            emoji = "🚲",
            hue = 210,
            target = 150_000,
            currencyCode = "USD",
            startDate = "2026-07-01",
            targetDate = "2026-12-31",
            accountId = "acc-1",
            status = "PAUSED",
            createdAt = 1,
            updatedAt = 2,
        )
    }

    // A blank accountId slips past the rules' nullable-string guard, and storing it
    // would break the Room FK — nothing has the id "".
    "a blank accountId decodes to null" {
        GoalDoc(accountId = "").toEntity(id = "goal-1", workspaceId = WORKSPACE)
            .accountId shouldBe null
    }

    "an absent accountId stays null" {
        GoalDoc().toEntity(id = "goal-1", workspaceId = WORKSPACE).accountId shouldBe null
    }

    "a blank status falls back to ACTIVE rather than an unparseable enum name" {
        GoalDoc(status = "").toEntity(id = "goal-1", workspaceId = WORKSPACE)
            .status shouldBe "ACTIVE"
    }

    "a contribution round-trips, keeping a negative amount signed" {
        val entity = GoalContributionDoc(
            goalId = "goal-1",
            amount = -2_500,
            occurredOn = "2026-07-10",
            note = "Oops",
            transferId = null,
            createdAt = 1,
            updatedAt = 2,
        ).toEntity(id = "gc-1", workspaceId = WORKSPACE)

        entity.amount shouldBe -2_500
        entity.goalId shouldBe "goal-1"
        entity.toDoc().amount shouldBe -2_500
    }
})
