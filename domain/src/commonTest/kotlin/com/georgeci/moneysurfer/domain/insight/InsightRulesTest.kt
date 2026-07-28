package com.georgeci.moneysurfer.domain.insight

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.aRecurringRule
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private val DINING = categoryId("cat-dining")
private val LEISURE = categoryId("cat-leisure")
private const val PERIOD = "2026-07"

class InsightRulesTest : StringSpec({

    "a category that jumped past the threshold reads as a warning" {
        val insights = generateInsights(
            input(
                current = listOf(slice(DINING, 380.dollars)),
                previous = listOf(slice(DINING, 300.dollars)),
            ),
        )

        val change = insights.categoryChanges().single()
        change.categoryId shouldBe DINING
        change.categoryName shouldBe "Dining"
        change.isIncrease shouldBe true
        change.tone shouldBe InsightTone.Warn
        change.changePercent shouldBe 27
    }

    "a category that fell past the threshold reads as a saving" {
        val insights = generateInsights(
            input(
                current = listOf(slice(LEISURE, 200.dollars)),
                previous = listOf(slice(LEISURE, 300.dollars)),
            ),
        )

        val change = insights.categoryChanges().single()
        change.categoryId shouldBe LEISURE
        change.isIncrease shouldBe false
        change.tone shouldBe InsightTone.Good
        change.changePercent shouldBe 33
    }

    "at most one rise and one fall are reported, and the biggest move in money wins" {
        val small = categoryId("cat-small")
        val insights = generateInsights(
            input(
                current = listOf(slice(DINING, 380.dollars), slice(small, 200.dollars)),
                previous = listOf(slice(DINING, 300.dollars), slice(small, 100.dollars)),
            ),
        )

        // Both rose; the €100 move beats the €80 one, and only one rise is kept.
        insights.categoryChanges().map { it.categoryId } shouldContainExactly listOf(small)
    }

    "a percentage jump on pocket change is filtered out, the one beside it is not" {
        val pocketChange = categoryId("cat-coffee")
        val insights = generateInsights(
            input(
                // 2 -> 5 is up 150%, but it is 0.3% of a 1000 month: noise, not a finding.
                current = listOf(slice(DINING, 380.dollars), slice(pocketChange, 5.dollars)),
                previous = listOf(slice(DINING, 300.dollars), slice(pocketChange, 2.dollars)),
            ),
        )

        insights.categoryChanges().map { it.categoryId } shouldContainExactly listOf(DINING)
    }

    "a category that moved less than a fifth says nothing" {
        val insights = generateInsights(
            input(
                current = listOf(slice(DINING, 340.dollars)),
                previous = listOf(slice(DINING, 300.dollars)),
            ),
        )

        insights.categoryChanges().shouldBeEmpty()
    }

    "a category with nothing behind it is not 'up infinity'" {
        val insights = generateInsights(
            input(
                current = listOf(slice(DINING, 400.dollars), slice(LEISURE, 300.dollars)),
                previous = listOf(slice(LEISURE, 300.dollars)),
            ),
        )

        insights.categoryChanges().shouldBeEmpty()
    }

    "a category that stopped entirely still counts as a saving" {
        val insights = generateInsights(
            input(
                current = listOf(slice(DINING, 300.dollars)),
                previous = listOf(slice(DINING, 300.dollars), slice(LEISURE, 120.dollars)),
            ),
        )

        val change = insights.categoryChanges().single()
        change.categoryId shouldBe LEISURE
        change.current shouldBe Money.zero()
        change.changePercent shouldBe 100
    }

    "the uncategorized bucket is a subject like any other" {
        val insights = generateInsights(
            input(
                current = listOf(slice(null, 400.dollars)),
                previous = listOf(slice(null, 300.dollars)),
            ),
        )

        val change = insights.categoryChanges().single()
        change.categoryId shouldBe null
        change.categoryName shouldBe null
        change.id shouldBe "category-change:uncategorized:$PERIOD"
    }

    "the period total is compared whichever way it moved" {
        val cases = listOf(
            Triple(400, SpendTrend.Up, InsightTone.Warn),
            Triple(200, SpendTrend.Down, InsightTone.Good),
            Triple(305, SpendTrend.Flat, InsightTone.Neutral),
        )

        cases.forEach { (current, expectedTrend, expectedTone) ->
            val insights = generateInsights(
                input(
                    current = listOf(slice(DINING, current.dollars)),
                    previous = listOf(slice(DINING, 300.dollars)),
                ),
            )

            val period = insights.periodSpend().shouldNotBeNull()
            period.trend shouldBe expectedTrend
            period.tone shouldBe expectedTone
            period.id shouldBe "period-spend:$PERIOD"
        }
    }

    "a first period has nothing to compare against" {
        val insights = generateInsights(
            input(current = listOf(slice(DINING, 400.dollars)), previous = emptyList()),
        )

        insights.shouldBeEmpty()
    }

    "only live schedules are counted, and each is converted to a month" {
        val insights = generateInsights(
            input(
                schedules = listOf(
                    aRecurringRule(amount = 10.dollars, frequency = RecurringFrequency.MONTHLY),
                    aRecurringRule(amount = 120.dollars, frequency = RecurringFrequency.YEARLY),
                    aRecurringRule(amount = 7.dollars, frequency = RecurringFrequency.WEEKLY),
                    aRecurringRule(amount = 99.dollars, isActive = false),
                ),
            ),
        )

        val subscriptions = insights.subscriptions().shouldNotBeNull()
        subscriptions.count shouldBe 3
        // 10 monthly + 120/12 yearly + 7 weekly over a 30-day month.
        subscriptions.monthlyTotal shouldBe (10 + 10 + 30).dollars
        subscriptions.tone shouldBe InsightTone.Neutral
        subscriptions.id shouldBe "active-subscriptions:$PERIOD"
    }

    "an interval divides the charge across the months it spans" {
        val insights = generateInsights(
            input(
                schedules = listOf(
                    aRecurringRule(amount = 60.dollars, frequency = RecurringFrequency.MONTHLY, interval = 3),
                ),
            ),
        )

        insights.subscriptions().shouldNotBeNull().monthlyTotal shouldBe 20.dollars
    }

    "no schedules at all means no sentence about them" {
        generateInsights(input(schedules = emptyList())).subscriptions() shouldBe null
    }

    "warnings come before wins, and both before the neutral facts" {
        val insights = generateInsights(
            input(
                current = listOf(slice(DINING, 400.dollars), slice(LEISURE, 100.dollars)),
                previous = listOf(slice(DINING, 300.dollars), slice(LEISURE, 300.dollars)),
                schedules = listOf(aRecurringRule()),
            ),
        )

        // Dining rose, Leisure fell, the total fell with it, and the subscriptions are a fact.
        insights.map { it.tone } shouldContainExactly listOf(
            InsightTone.Warn,
            InsightTone.Good,
            InsightTone.Good,
            InsightTone.Neutral,
        )
    }

    "an id names the finding and the period, never the numbers" {
        fun idsFor(current: Money) = generateInsights(
            input(
                current = listOf(slice(DINING, current)),
                previous = listOf(slice(DINING, 300.dollars)),
            ),
        ).map { it.id }

        // The badge counts new findings, so the same rise must not read as a new one every time
        // another receipt lands in the category.
        idsFor(400.dollars) shouldContainExactly idsFor(420.dollars)
    }
})

private fun input(
    current: List<CategorySpendSlice> = emptyList(),
    previous: List<CategorySpendSlice> = emptyList(),
    schedules: List<RecurringRule> = emptyList(),
) = InsightInput(
    periodKey = PERIOD,
    currency = EUR,
    currentSpend = current,
    previousSpend = previous,
    categoryNames = mapOf(DINING to "Dining", LEISURE to "Leisure"),
    expenseSchedules = schedules,
)

private fun slice(categoryId: CategoryId?, total: Money) =
    CategorySpendSlice(categoryId = categoryId, total = total, transactionCount = 1)

private fun List<Insight>.categoryChanges(): List<Insight.CategoryChange> =
    filterIsInstance<Insight.CategoryChange>()

private fun List<Insight>.periodSpend(): Insight.PeriodSpend? =
    filterIsInstance<Insight.PeriodSpend>().firstOrNull()

private fun List<Insight>.subscriptions(): Insight.ActiveSubscriptions? =
    filterIsInstance<Insight.ActiveSubscriptions>().firstOrNull()
