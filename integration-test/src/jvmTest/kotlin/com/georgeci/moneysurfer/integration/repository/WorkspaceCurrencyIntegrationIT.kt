package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * `UpdateWorkspaceCurrencyUseCase` against real Room. The first-run currency picker calls
 * this use case to persist the user's choice — the workspace row's `baseCurrency` column and
 * the seeded account's `currencyCode` must both round-trip the change.
 */
class WorkspaceCurrencyIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack

    val gel = CurrencyCode("GEL")

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
    }

    afterEach { harness.close() }

    "updates the workspace baseCurrency column in Room" {
        stack.updateWorkspaceCurrency(DEFAULT_WORKSPACE_ID, gel).isRight() shouldBe true

        stack.workspaceRepository.getById(DEFAULT_WORKSPACE_ID)!!.baseCurrency shouldBe gel
    }

    "re-currencies the seeded account so the Dashboard total follows the choice" {
        stack.accountRepository.insert(
            anAccount(id = accountId("a-cash"), workspaceId = DEFAULT_WORKSPACE_ID, currencyCode = USD),
        )

        stack.updateWorkspaceCurrency(DEFAULT_WORKSPACE_ID, gel).isRight() shouldBe true

        val account = stack.accountRepository.getByWorkspaceId(DEFAULT_WORKSPACE_ID).first().single()
        account.currencyCode shouldBe gel
    }
})
