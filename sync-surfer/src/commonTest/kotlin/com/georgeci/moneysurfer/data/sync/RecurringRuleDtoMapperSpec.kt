package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.remote.RecurringRuleDoc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val WORKSPACE = "ws-1"

class RecurringRuleDtoMapperSpec : StringSpec({

    "a recurring rule round-trips through the wire format" {
        val doc = RecurringRuleDoc(
            title = "Rent",
            amount = 120_000,
            categoryId = "cat-1",
            scheduleFrequency = "MONTHLY",
            scheduleInterval = 1,
            scheduleDaysOfMonth = listOf(31),
            scheduleMissingDayPolicy = "LAST_DAY_OF_MONTH",
            startDate = "2026-07-01",
            nextRunAt = 1_700_000_000_000,
            autoCreate = true,
            isActive = true,
            createdAt = 1,
            updatedAt = 2,
        )

        val entity = doc.toEntity(id = "rule-1", workspaceId = WORKSPACE)

        entity.id shouldBe "rule-1"
        entity.workspaceId shouldBe WORKSPACE
        entity.toDoc().copy(clientVersionCode = 1) shouldBe doc
    }

    // Room keeps the day sets as CSV columns; the wire carries real lists.
    "day sets convert between CSV columns and wire lists" {
        val entity = RecurringRuleDoc(
            scheduleFrequency = "WEEKLY",
            scheduleDaysOfWeek = listOf("MONDAY", "FRIDAY"),
            scheduleDaysOfMonth = listOf(1, 15),
        ).toEntity(id = "rule-1", workspaceId = WORKSPACE)

        entity.scheduleDaysOfWeek shouldBe "MONDAY,FRIDAY"
        entity.scheduleDaysOfMonth shouldBe "1,15"

        entity.toDoc().scheduleDaysOfWeek shouldBe listOf("MONDAY", "FRIDAY")
        entity.toDoc().scheduleDaysOfMonth shouldBe listOf(1, 15)
    }

    // A legacy `""` or a trailing comma must decode to an empty list, not a blank day.
    "blank CSV segments drop out of the wire lists" {
        val entity = RecurringRuleDoc().toEntity(id = "rule-1", workspaceId = WORKSPACE)
            .copy(scheduleDaysOfWeek = "MONDAY,", scheduleDaysOfMonth = "")

        entity.toDoc().scheduleDaysOfWeek shouldBe listOf("MONDAY")
        entity.toDoc().scheduleDaysOfMonth shouldBe emptyList()
    }

    // Blank enum names slip past the rules' string guard but store an unparseable value.
    "blank enum names fall back to the repository's parse defaults" {
        val entity = RecurringRuleDoc(
            scheduleFrequency = "",
            scheduleMissingDayPolicy = "",
        ).toEntity(id = "rule-1", workspaceId = WORKSPACE)

        entity.scheduleFrequency shouldBe "MONTHLY"
        entity.scheduleMissingDayPolicy shouldBe "LAST_DAY_OF_MONTH"
    }

    "an absent nextRunAt stays null" {
        RecurringRuleDoc().toEntity(id = "rule-1", workspaceId = WORKSPACE)
            .nextRunAt shouldBe null
    }
})
