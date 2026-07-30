package com.georgeci.moneysurfer.domain.debug

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * What the generator is allowed to know about the workspace it is filling. Assembled by
 * `DemoDataWriter`, which owns every repository; the generator itself does no IO so its output is
 * reproducible for a given seed and clock.
 */
internal data class DemoDataSnapshot(
    val workspaceId: WorkspaceId,
    val currency: CurrencyCode,
    val accounts: List<Account>,
    val categories: List<Category>,
    val budgetNames: Set<String>,
    val goalTitles: Set<String>,
)

/**
 * Rows to insert, in dependency order: [accounts] before [transactions] (which reference them),
 * [goals] before [contributions].
 */
internal data class DemoDataPlan(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val goals: List<SavingsGoal>,
    val contributions: List<GoalContribution>,
)

/** A year of history is what the dashboard's trailing-months widgets need to draw a full curve. */
private const val HISTORY_MONTHS = 12

/** Rows per run, chosen so lists page and charts look inhabited without a slow first launch. */
private const val TRANSACTION_COUNT = 300

private const val EARLIEST_HOUR = 8
private const val LATEST_HOUR = 22
private const val MINUTES_PER_HOUR = 60

/**
 * The default [buildDemoDataPlan] seed. Derived from the clock rather than fixed: the only other
 * input that moves between two runs is the calendar day, so a constant would make a second run the
 * same day replay the first one row for row — 300 duplicates instead of a denser year. Pass an
 * explicit `seed` where reproducibility is the point (screenshot diffs, tests).
 */
private fun seedFrom(now: Instant): Int = now.toEpochMilliseconds().toInt()

/**
 * Builds one batch of demo rows against [snapshot].
 *
 * Accounts, budgets and goals already present under the same name are left alone, so a second run
 * only deepens the transaction history instead of duplicating the scaffolding around it.
 */
internal fun buildDemoDataPlan(
    snapshot: DemoDataSnapshot,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    seed: Int = seedFrom(now),
): DemoDataPlan {
    val random = Random(seed)
    val today = now.toLocalDateTime(zone).date
    val windowStart = LocalDate(today.year, today.month, 1).minus(HISTORY_MONTHS - 1, DateTimeUnit.MONTH)

    val newAccounts = snapshot.newAccounts(now)
    // Archived accounts are hidden from every list and dropped from total and budget rollups, so a
    // row written against one is invisible in the app while still counted in the report. Plan
    // against the active set only — `newAccounts` applies the same filter to its name dedup, so an
    // archived "Cash" gets an active replacement rather than silently absorbing the batch.
    val accounts = (snapshot.accounts + newAccounts).filterNot { it.archived }
    // Nothing to hang transactions off. Only reachable if the account seeds below are emptied.
    if (accounts.isEmpty()) return DemoDataPlan(newAccounts, emptyList(), emptyList(), emptyList(), emptyList())

    val ledger = LedgerContext(
        workspaceId = snapshot.workspaceId,
        accounts = accounts,
        categories = snapshot.categories,
        now = now,
        zone = zone,
        random = random,
    )

    val openings = newAccounts.map { account -> ledger.openingBalance(account, windowStart) }
    val bills = ledger.monthlyBills(windowStart, today)
    val discretionary = ledger.discretionary(windowStart, today, TRANSACTION_COUNT - bills.size)

    return DemoDataPlan(
        accounts = newAccounts,
        transactions = openings + bills + discretionary,
        budgets = snapshot.newBudgets(today, now),
        goals = snapshot.newGoals(accounts, today, now),
        contributions = emptyList(),
    ).withGoalContributions(now)
}

// --- accounts -------------------------------------------------------------------------------

private data class AccountSeed(val name: String, val type: AccountType, val openingMinor: Long)

private val ACCOUNT_SEEDS = listOf(
    AccountSeed("Cash", AccountType.CASH, 32_000),
    AccountSeed("Checking", AccountType.BANK, 425_000),
    AccountSeed("Savings", AccountType.SAVINGS, 1_200_000),
)

private fun DemoDataSnapshot.newAccounts(now: Instant): List<Account> {
    // Archived rows do not count as taken: the seed name is free again, and re-using an archived
    // account would file the whole batch somewhere the app never shows.
    val taken = accounts.filterNot { it.archived }.map { it.name }.toSet()
    // Positions are still assigned past every row, archived included, so nothing collides.
    val nextSortOrder = (accounts.maxOfOrNull { it.sortOrder } ?: -1) + 1
    return ACCOUNT_SEEDS
        .filterNot { it.name in taken }
        .mapIndexed { index, seed ->
            Account(
                id = AccountId.uuid(),
                workspaceId = workspaceId,
                name = seed.name,
                type = seed.type,
                currencyCode = currency,
                // Zero on the row: the opening amount arrives as its own OPENING_BALANCE
                // transaction, so the cached balance stays a projection of the ledger.
                balance = Money.zero(),
                updatedAt = now,
                sortOrder = nextSortOrder + index,
            )
        }
}

private fun openingMinorFor(account: Account): Long =
    ACCOUNT_SEEDS.firstOrNull { it.name == account.name }?.openingMinor ?: 0

// --- transactions ---------------------------------------------------------------------------

/** A predictable row that repeats every month — what makes the monthly charts look like a life. */
private data class MonthlyBill(
    val categoryName: String,
    val merchant: String,
    val dayOfMonth: Int,
    val minMinor: Long,
    val maxMinor: Long,
    val type: TransactionType,
)

private val MONTHLY_BILLS = listOf(
    MonthlyBill("Salary", "Acme Corp", 5, 320_000, 340_000, TransactionType.INCOME),
    MonthlyBill("Rent", "Landlord", 3, 115_000, 115_000, TransactionType.EXPENSE),
    MonthlyBill("Utilities", "City Utilities", 12, 8_500, 14_000, TransactionType.EXPENSE),
    MonthlyBill("Subscriptions", "Streaming Plus", 20, 999, 2_999, TransactionType.EXPENSE),
)

/** One spending habit. [weight] is how many of the random rows it claims, relative to its peers. */
private data class SpendProfile(
    val categoryName: String,
    val type: TransactionType,
    val weight: Int,
    val minMinor: Long,
    val maxMinor: Long,
    val merchants: List<String>,
)

private val SPEND_PROFILES = listOf(
    SpendProfile(
        "Groceries",
        TransactionType.EXPENSE,
        weight = 26,
        minMinor = 1_200,
        maxMinor = 14_000,
        merchants = listOf("Green Market", "Corner Grocer", "Farmers Market", "SuperFresh"),
    ),
    SpendProfile(
        "Restaurants",
        TransactionType.EXPENSE,
        weight = 20,
        minMinor = 800,
        maxMinor = 9_500,
        merchants = listOf("Ramen Ya", "Blue Bottle", "Pizzeria Napoli", "Sushi Bar", "Cafe Roma"),
    ),
    SpendProfile(
        "Transport",
        TransactionType.EXPENSE,
        weight = 16,
        minMinor = 250,
        maxMinor = 6_000,
        merchants = listOf("City Metro", "Ride Share", "Fuel Station", "Bike Rental"),
    ),
    SpendProfile(
        "Shopping",
        TransactionType.EXPENSE,
        weight = 10,
        minMinor = 1_500,
        maxMinor = 25_000,
        merchants = listOf("Home Store", "Outfitters", "Electronics Hub", "Bookshop"),
    ),
    SpendProfile(
        "Entertainment",
        TransactionType.EXPENSE,
        weight = 8,
        minMinor = 900,
        maxMinor = 7_000,
        merchants = listOf("Cinema City", "Game Store", "Concert Hall", "Bowling Lane"),
    ),
    SpendProfile(
        "Health",
        TransactionType.EXPENSE,
        weight = 6,
        minMinor = 1_000,
        maxMinor = 18_000,
        merchants = listOf("Pharmacy", "Dental Clinic", "Optics"),
    ),
    SpendProfile(
        "Travel",
        TransactionType.EXPENSE,
        weight = 4,
        minMinor = 6_000,
        maxMinor = 60_000,
        merchants = listOf("Airline", "Hotel Booking", "Rail Europe"),
    ),
    SpendProfile(
        "Refunds",
        TransactionType.INCOME,
        weight = 3,
        minMinor = 1_200,
        maxMinor = 12_000,
        merchants = listOf("Online Store", "Tax Office"),
    ),
    SpendProfile(
        "Bonus",
        TransactionType.INCOME,
        weight = 2,
        minMinor = 40_000,
        maxMinor = 90_000,
        merchants = listOf("Acme Corp"),
    ),
)

/** Everything a single transaction needs that does not vary row to row. */
private class LedgerContext(
    val workspaceId: WorkspaceId,
    val accounts: List<Account>,
    categories: List<Category>,
    val now: Instant,
    val zone: TimeZone,
    val random: Random,
) {
    private val byName: Map<String, Category> = categories.associateBy { it.name }
    private val fallbackByType: Map<CategoryType, Category> =
        categories.groupBy { it.type }.mapValues { (_, group) -> group.first() }

    /** Savings sit out day-to-day spending, so the balance curve on them stays a savings curve. */
    private val spendAccounts: List<Account> =
        accounts.filterNot { it.type == AccountType.SAVINGS }.ifEmpty { accounts }

    private val salaryAccount: Account =
        accounts.firstOrNull { it.type == AccountType.BANK } ?: spendAccounts.first()

    /**
     * Named category if the workspace still has it, otherwise any category of the right type — a
     * renamed default must not silently produce uncategorized rows.
     */
    private fun categoryId(name: String, type: TransactionType): CategoryId? {
        val wanted = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return byName[name]?.takeIf { it.type == wanted }?.id ?: fallbackByType[wanted]?.id
    }

    fun openingBalance(account: Account, date: LocalDate): Transaction = transaction(
        account = account,
        money = Money.fromMinor(openingMinorFor(account)),
        categoryId = null,
        merchant = "",
        date = date,
        type = TransactionType.OPENING_BALANCE,
    )

    fun monthlyBills(windowStart: LocalDate, today: LocalDate): List<Transaction> =
        (0 until HISTORY_MONTHS).flatMap { monthIndex ->
            val anchor = windowStart.plus(monthIndex, DateTimeUnit.MONTH)
            MONTHLY_BILLS.mapNotNull { bill ->
                val date = anchor.plus(bill.dayOfMonth - 1, DateTimeUnit.DAY)
                // The current month is only part-way through; a rent row dated next week would
                // read as a planned expense the app has no concept of here.
                if (date > today) return@mapNotNull null
                transaction(
                    // Salary in and the fixed bills out both run through the current account.
                    account = salaryAccount,
                    money = Money.fromMinor(random.nextLong(bill.minMinor, bill.maxMinor + 1)),
                    categoryId = categoryId(bill.categoryName, bill.type),
                    merchant = bill.merchant,
                    date = date,
                    type = bill.type,
                )
            }
        }

    fun discretionary(windowStart: LocalDate, today: LocalDate, count: Int): List<Transaction> {
        if (count <= 0) return emptyList()
        val weighted = SPEND_PROFILES.flatMap { profile -> List(profile.weight) { profile } }
        val daySpan = windowStart.daysUntil(today)
        return List(count) {
            val profile = weighted[random.nextInt(weighted.size)]
            transaction(
                account = spendAccounts[random.nextInt(spendAccounts.size)],
                money = Money.fromMinor(random.nextLong(profile.minMinor, profile.maxMinor + 1)),
                categoryId = categoryId(profile.categoryName, profile.type),
                merchant = profile.merchants[random.nextInt(profile.merchants.size)],
                date = windowStart.plus(random.nextInt(daySpan + 1), DateTimeUnit.DAY),
                type = profile.type,
            )
        }
    }

    private fun transaction(
        account: Account,
        money: Money,
        categoryId: CategoryId?,
        merchant: String,
        date: LocalDate,
        type: TransactionType,
    ): Transaction = Transaction(
        id = TransactionId.uuid(),
        workspaceId = workspaceId,
        accountId = account.id,
        money = money,
        currencyCode = account.currencyCode,
        categoryId = categoryId,
        note = "",
        merchant = merchant,
        operationAt = operationAt(date),
        operationDate = date,
        type = type,
        createdAt = now,
        updatedAt = now,
    )

    /**
     * A plausible time of day, clamped to [now] so a row dated today cannot land in the future and
     * sort ahead of everything the tester adds by hand afterwards.
     */
    private fun operationAt(date: LocalDate): Instant {
        val at = date.atStartOfDayIn(zone) +
            random.nextInt(EARLIEST_HOUR, LATEST_HOUR).hours +
            random.nextInt(MINUTES_PER_HOUR).minutes
        return if (at > now) now else at
    }
}

// --- budgets --------------------------------------------------------------------------------

private data class BudgetSeed(val name: String, val categoryNames: List<String>, val amountMinor: Long)

private val BUDGET_SEEDS = listOf(
    BudgetSeed("Food", listOf("Groceries", "Restaurants"), 60_000),
    // No categories at all — the "everything" shape, which resolves to every expense category.
    BudgetSeed("Monthly cap", emptyList(), 250_000),
)

private fun DemoDataSnapshot.newBudgets(today: LocalDate, now: Instant): List<Budget> {
    val byName = categories.associateBy { it.name }
    return BUDGET_SEEDS
        .filterNot { it.name in budgetNames }
        .map { seed ->
            Budget(
                id = BudgetId.uuid(),
                workspaceId = workspaceId,
                name = seed.name,
                categoryIds = seed.categoryNames.mapNotNull { byName[it]?.id },
                amount = Money.fromMinor(seed.amountMinor),
                period = BudgetPeriod.MONTHLY,
                startDate = LocalDate(today.year, today.month, 1),
                createdAt = now,
            )
        }
}

// --- goals ----------------------------------------------------------------------------------

private data class GoalSeed(
    val title: String,
    val emoji: String,
    val hue: Int,
    val targetMinor: Long,
    val startedMonthsAgo: Int,
    val dueInMonths: Int,
    val contributions: Int,
    val contributionMinor: Long,
)

private val GOAL_SEEDS = listOf(
    GoalSeed(
        title = "Trip to Lisbon",
        emoji = "🏖",
        hue = 200,
        targetMinor = 250_000,
        startedMonthsAgo = 6,
        dueInMonths = 6,
        contributions = 5,
        contributionMinor = 30_000,
    ),
    GoalSeed(
        title = "New laptop",
        emoji = "💻",
        hue = 265,
        targetMinor = 180_000,
        startedMonthsAgo = 3,
        dueInMonths = 9,
        contributions = 3,
        contributionMinor = 25_000,
    ),
)

private fun DemoDataSnapshot.newGoals(accounts: List<Account>, today: LocalDate, now: Instant): List<SavingsGoal> {
    val savings = accounts.firstOrNull { it.type == AccountType.SAVINGS }
    return GOAL_SEEDS
        .filterNot { it.title in goalTitles }
        .map { seed ->
            val startDate = today.minus(seed.startedMonthsAgo, DateTimeUnit.MONTH)
            SavingsGoal(
                id = GoalId.uuid(),
                workspaceId = workspaceId,
                title = seed.title,
                emoji = seed.emoji,
                hue = seed.hue,
                target = Money.fromMinor(seed.targetMinor),
                currencyCode = currency,
                startDate = startDate,
                targetDate = today.plus(seed.dueInMonths, DateTimeUnit.MONTH),
                accountId = savings?.id,
                createdAt = now,
            )
        }
}

/**
 * Back-fills each freshly created goal with monthly contributions, so the progress ring shows
 * something other than zero. Attached after the fact because a contribution needs the goal's id.
 */
private fun DemoDataPlan.withGoalContributions(now: Instant): DemoDataPlan {
    val byTitle = GOAL_SEEDS.associateBy { it.title }
    val contributions = goals.flatMap { goal ->
        val seed = byTitle[goal.title] ?: return@flatMap emptyList()
        List(seed.contributions) { index ->
            GoalContribution(
                id = GoalContributionId.uuid(),
                workspaceId = goal.workspaceId,
                goalId = goal.id,
                amount = Money.fromMinor(seed.contributionMinor),
                occurredOn = goal.startDate.plus(index, DateTimeUnit.MONTH),
                createdAt = now,
            )
        }
    }
    return copy(contributions = contributions)
}
