package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeCategoryRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeRecurringRuleRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aRecurringRule
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.insight.Insight
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

private val WS = workspaceId("ws-1")
private val DINING = categoryId("cat-dining")
private val SALARY = categoryId("cat-salary")

class GenerateInsightsUseCaseTest : StringSpec({

    "nobody signed in means nothing to say" {
        val useCase = newUseCase(workspace = null, today = LocalDate(2026, 7, 10))

        useCase(TimeZone.UTC).first().shouldBeEmpty()
    }

    "a workspace row the device has not pulled yet emits nothing rather than a wrong total" {
        // The aggregates filter on the base currency, so a null one matches no transaction at all.
        val useCase = newUseCase(
            workspace = null,
            currentWorkspaceId = WS,
            today = LocalDate(2026, 7, 10),
        )

        useCase(TimeZone.UTC).first().shouldBeEmpty()
    }

    "the baseline is the same stretch of the previous month, not the whole of it" {
        val analytics = FakeSpendAnalyticsRepository()
        val useCase = newUseCase(today = LocalDate(2026, 7, 10), analytics = analytics)

        useCase(TimeZone.UTC).first()

        analytics.byCategoryScopes.map { it.window } shouldContainExactly listOf(
            TransactionPeriodWindow(LocalDate(2026, 7, 1), LocalDate(2026, 7, 10)),
            TransactionPeriodWindow(LocalDate(2026, 6, 1), LocalDate(2026, 6, 10)),
        )
    }

    "the baseline never spills past the end of its own month" {
        val analytics = FakeSpendAnalyticsRepository()
        val useCase = newUseCase(today = LocalDate(2026, 3, 31), analytics = analytics)

        useCase(TimeZone.UTC).first()

        analytics.byCategoryScopes.map { it.window } shouldContainExactly listOf(
            TransactionPeriodWindow(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)),
            // 30 days after 1 February is 3 March; February is where it stops.
            TransactionPeriodWindow(LocalDate(2026, 2, 1), LocalDate(2026, 2, 28)),
        )
    }

    "the rules run over both windows and the category names are resolved" {
        val today = LocalDate(2026, 7, 10)
        val useCase = newUseCase(
            today = today,
            analytics = FakeSpendAnalyticsRepository(
                mapOf(
                    TransactionPeriodWindow(LocalDate(2026, 7, 1), today) to
                        listOf(CategorySpendSlice(DINING, 400.dollars, 8)),
                    TransactionPeriodWindow(LocalDate(2026, 6, 1), LocalDate(2026, 6, 10)) to
                        listOf(CategorySpendSlice(DINING, 300.dollars, 6)),
                ),
            ),
        )

        val change = useCase(TimeZone.UTC).first()
            .filterIsInstance<Insight.CategoryChange>()
            .single()

        change.categoryName shouldBe "Dining"
        change.currency shouldBe EUR
        change.id shouldBe "category-change:${DINING.value}:2026-07"
    }

    "a salary paid in every month is a schedule, not a subscription" {
        val useCase = newUseCase(
            today = LocalDate(2026, 7, 10),
            rules = FakeRecurringRuleRepository(
                listOf(
                    aRecurringRule(workspaceId = WS, categoryId = DINING, amount = 12.dollars),
                    aRecurringRule(workspaceId = WS, categoryId = SALARY, amount = 3_000.dollars),
                ),
            ),
        )

        val subscriptions = useCase(TimeZone.UTC).first()
            .filterIsInstance<Insight.ActiveSubscriptions>()
            .single()

        subscriptions.count shouldBe 1
        subscriptions.monthlyTotal shouldBe 12.dollars
    }
})

private fun newUseCase(
    today: LocalDate,
    workspace: WorkspaceId? = WS,
    currentWorkspaceId: WorkspaceId? = workspace,
    analytics: FakeSpendAnalyticsRepository = FakeSpendAnalyticsRepository(),
    rules: FakeRecurringRuleRepository = FakeRecurringRuleRepository(),
) = GenerateInsightsUseCase(
    spendAnalytics = analytics,
    categoryRepository = FakeCategoryRepository(
        listOf(
            aCategory(id = DINING, workspaceId = WS, name = "Dining", type = CategoryType.EXPENSE),
            aCategory(id = SALARY, workspaceId = WS, name = "Salary", type = CategoryType.INCOME),
        ),
    ),
    recurringRuleRepository = rules,
    workspaceRepository = FakeGoalWorkspaceRepository(
        listOfNotNull(workspace?.let { aWorkspace(id = it, baseCurrency = EUR) }),
    ),
    session = InMemorySessionPointers(currentWorkspaceId = currentWorkspaceId),
    clock = ClockUseCase(FixedClock(today.atStartOfDayIn(TimeZone.UTC))),
)
