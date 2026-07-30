package com.georgeci.moneysurfer.domain.debug

import arrow.core.left
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.constants.DEFAULT_CATEGORY_SEEDS
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

private val NOW = Instant.parse("2026-07-30T10:15:00Z")
private val WORKSPACE = WorkspaceId.uuid()
private val EUR = CurrencyCode("EUR")

/**
 * The prefiller is a QA tool, so the properties worth pinning are the ones a tester would only
 * notice hours later: that the rows land in the *current* workspace, that balances move with the
 * ledger instead of staying at the opening amount, that a second run does not clone the scaffolding
 * around the transactions, and that a guest session never asks for a push.
 */
class DebugDataPrefillerTest : StringSpec({

    "refuses to invent a workspace when none is pinned" {
        runTest {
            val env = PrefillEnv(pinnedWorkspace = null)

            env.prefiller.prefill() shouldBe DebugPrefillError.NoWorkspace.left()

            env.repos.accounts.rows.shouldBeEmpty()
            env.repos.transactions.rows.shouldBeEmpty()
        }
    }

    "fills the pinned workspace with accounts, a year of transactions, budgets and goals" {
        runTest {
            val env = PrefillEnv()

            val report = env.prefiller.prefill().getOrNull()

            report shouldNotBe null
            report!!.accounts shouldBe 3
            report.transactions shouldBeGreaterThan 200
            report.budgets shouldBe 2
            report.goals shouldBe 2

            env.repos.accounts.rows.map { it.name } shouldContainAll listOf("Cash", "Checking", "Savings")
            env.repos.transactions.rows.all { it.workspaceId == WORKSPACE } shouldBe true
            env.repos.contributions.rows.size shouldBeGreaterThan 0
        }
    }

    "seeds the default categories when the workspace has none, and categorizes against them" {
        runTest {
            val env = PrefillEnv()

            val report = env.prefiller.prefill().getOrNull()!!

            report.categories shouldBe DEFAULT_CATEGORY_SEEDS.size
            // Opening balances are deliberately uncategorized; everything else must land somewhere,
            // or every category-shaped screen comes up empty on a freshly prefilled install.
            env.repos.transactions.rows
                .filter { it.type != TransactionType.OPENING_BALANCE }
                .all { it.categoryId != null } shouldBe true
        }
    }

    "moves account balances, so the ledger and the cached balance agree" {
        runTest {
            val env = PrefillEnv()

            env.prefiller.prefill()

            val checking = env.repos.accounts.rows.first { it.name == "Checking" }
            // Salary in, rent and the discretionary rows out — the only wrong answer is the
            // opening amount, which is what a raw insert that skipped the single writer would give.
            env.repos.accounts.balanceOf(checking.id) shouldNotBe checking.balance
            env.repos.accounts.balanceOf(checking.id).minor shouldBeGreaterThan 0L
        }
    }

    "a second run deepens the history without cloning accounts, budgets or goals" {
        runTest {
            val env = PrefillEnv()

            val first = env.prefiller.prefill().getOrNull()!!
            val second = env.prefiller.prefill().getOrNull()!!

            second.accounts shouldBe 0
            second.categories shouldBe 0
            second.budgets shouldBe 0
            second.goals shouldBe 0
            second.transactions shouldBeGreaterThan 0

            env.repos.accounts.rows.size shouldBe 3
            env.repos.budgets.rows.size shouldBe 2
            env.repos.goals.rows.size shouldBe 2
            env.repos.transactions.rows.size shouldBe first.transactions + second.transactions
        }
    }

    "a guest session never asks for a push — demo data must not reach Firestore" {
        runTest {
            val env = PrefillEnv(firebaseUid = null)

            env.prefiller.prefill()

            env.coordinator.requests.shouldBeEmpty()
        }
    }

    "a signed-in session drains the outbox rather than waiting for the next tick" {
        runTest {
            val env = PrefillEnv(firebaseUid = "uid-1")

            env.prefiller.prefill()

            env.coordinator.requests.size shouldBe 1
        }
    }

    "uses the workspace base currency for the accounts it creates" {
        runTest {
            val env = PrefillEnv(currency = EUR)

            env.prefiller.prefill()

            env.repos.accounts.rows.all { it.currencyCode == EUR } shouldBe true
            env.repos.goals.rows.all { it.currencyCode == EUR } shouldBe true
        }
    }
})

private class PrefillEnv(
    pinnedWorkspace: WorkspaceId? = WORKSPACE,
    firebaseUid: String? = null,
    currency: CurrencyCode = CurrencyCode("USD"),
) {
    val repos = PrefillRepos()
    val coordinator = RecordingSyncCoordinator()

    private val session = InMemorySessionPointers(
        currentWorkspaceId = pinnedWorkspace,
        currentFirebaseUid = firebaseUid,
    )

    private val workspaces = FakeGoalWorkspaceRepository(
        listOf(aWorkspace(id = WORKSPACE, baseCurrency = currency)),
    )

    val prefiller = DebugDataPrefillerImpl(
        session = session,
        workspaceRepository = workspaces,
        writer = DemoDataWriter(
            accountRepository = repos.accounts,
            categoryRepository = repos.categories,
            budgetRepository = repos.budgets,
            savingsGoalRepository = repos.goals,
            goalContributionRepository = repos.contributions,
            applyTransactionChange = ApplyTransactionChangeUseCase(repos.transactions, repos.accounts),
        ),
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase(FixedClock(NOW))),
        syncCoordinator = coordinator,
    )
}

private class PrefillRepos {
    val accounts = RecordingAccountRepository()
    val categories = RecordingCategoryRepository()
    val budgets = RecordingBudgetRepository()
    val transactions = RecordingTransactionRepository()
    val goals = FakeSavingsGoalRepository()
    val contributions = FakeGoalContributionRepository()
}
