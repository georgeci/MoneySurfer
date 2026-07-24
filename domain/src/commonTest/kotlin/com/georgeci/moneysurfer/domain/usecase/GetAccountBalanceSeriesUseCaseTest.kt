package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.model.AccountBalanceSeries
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

class GetAccountBalanceSeriesUseCaseTest : StringSpec({

    val account = accountId()
    val today = LocalDate(2024, 3, 31)
    val useCase = GetAccountBalanceSeriesUseCase(
        clock = ClockUseCase(FixedClock(today.atStartOfDayIn(TimeZone.UTC))),
    )

    "the window ends on the clock's today and defaults to a month" {
        val series = useCase(
            accountId = account,
            currentBalance = 500.dollars,
            transactions = emptyList(),
            timeZone = TimeZone.UTC,
        )

        series.points shouldHaveSize AccountBalanceSeries.DEFAULT_DAYS
        series.points.last().date shouldBe today
        series.points.last().balance shouldBe 500.dollars
    }

    "history inside the window moves the curve" {
        val series = useCase(
            accountId = account,
            currentBalance = 500.dollars,
            transactions = listOf(
                aTransaction(
                    accountId = account,
                    money = 120.dollars,
                    type = TransactionType.INCOME,
                    operationDate = today.minus(1, DateTimeUnit.DAY),
                ),
            ),
            days = 4,
            timeZone = TimeZone.UTC,
        )

        series.points.map { it.balance } shouldBe
            listOf(380.dollars, 380.dollars, 500.dollars, 500.dollars)
        series.delta shouldBe 120.dollars
    }
})
