package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AccountWriteUseCasesTest : StringSpec({

    "create stores the account" {
        val repo = FakeWritableAccountRepository()
        val account = anAccount(id = accountId("a-1"))

        CreateAccountUseCase(repo).invoke(account) shouldBe Either.Right(Unit)
        repo.store[account.id] shouldBe account
    }

    "create surfaces a repository failure instead of throwing" {
        val repo = FakeWritableAccountRepository(failWrites = true)

        val result = CreateAccountUseCase(repo).invoke(anAccount(id = accountId("a-1")))

        result.shouldBeInstanceOf<Either.Left<AccountActionError>>()
        result.value.shouldBeInstanceOf<AccountActionError.LocalWriteFailed>()
    }

    "update applies the edit to the stored row and leaves the rest alone" {
        val existing = anAccount(id = accountId("a-1"), name = "Everyday", type = AccountType.CASH)
        val repo = FakeWritableAccountRepository(mutableMapOf(existing.id to existing))

        val result = UpdateAccountUseCase(repo).invoke(existing.id) {
            it.copy(name = "Daily", type = AccountType.BANK)
        }

        result shouldBe Either.Right(Unit)
        val saved = repo.store.getValue(existing.id)
        saved.name shouldBe "Daily"
        saved.type shouldBe AccountType.BANK
        saved.currencyCode shouldBe existing.currencyCode
        saved.balance shouldBe existing.balance
    }

    "update on a missing account returns AccountNotFound and writes nothing" {
        val repo = FakeWritableAccountRepository()

        val result = UpdateAccountUseCase(repo).invoke(accountId("missing")) { it }

        result.shouldBeInstanceOf<Either.Left<AccountActionError>>()
        result.value shouldBe AccountActionError.AccountNotFound
        repo.store.isEmpty() shouldBe true
    }

    "delete removes the account" {
        val existing = anAccount(id = accountId("a-1"))
        val repo = FakeWritableAccountRepository(mutableMapOf(existing.id to existing))

        DeleteAccountUseCase(repo).invoke(existing.id) shouldBe Either.Right(Unit)
        repo.store.isEmpty() shouldBe true
    }

    "delete on a missing account returns AccountNotFound" {
        val repo = FakeWritableAccountRepository()

        val result = DeleteAccountUseCase(repo).invoke(accountId("missing"))

        result.shouldBeInstanceOf<Either.Left<AccountActionError>>()
        result.value shouldBe AccountActionError.AccountNotFound
    }

    "reorder numbers the accounts in the order it was given" {
        val first = anAccount(id = accountId("a-1"), sortOrder = 0)
        val second = anAccount(id = accountId("a-2"), sortOrder = 1)
        val third = anAccount(id = accountId("a-3"), sortOrder = 2)
        val repo = FakeWritableAccountRepository(
            mutableMapOf(first.id to first, second.id to second, third.id to third),
        )

        val result = ReorderAccountsUseCase(repo).invoke(listOf(third.id, first.id, second.id))

        result shouldBe Either.Right(Unit)
        repo.store.getValue(third.id).sortOrder shouldBe 0
        repo.store.getValue(first.id).sortOrder shouldBe 1
        repo.store.getValue(second.id).sortOrder shouldBe 2
    }

    "reorder surfaces a repository failure instead of throwing" {
        val repo = FakeWritableAccountRepository(failWrites = true)

        val result = ReorderAccountsUseCase(repo).invoke(listOf(accountId("a-1")))

        result.shouldBeInstanceOf<Either.Left<AccountActionError>>()
        result.value.shouldBeInstanceOf<AccountActionError.LocalWriteFailed>()
    }
})

private class FakeWritableAccountRepository(
    val store: MutableMap<AccountId, Account> = mutableMapOf(),
    private val failWrites: Boolean = false,
) : AccountRepository {
    override fun getAll(): Flow<List<Account>> = flowOf(store.values.toList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getById(id: AccountId): Account? = store[id]
    override suspend fun insert(account: Account) {
        if (failWrites) error("disk full")
        store[account.id] = account
    }
    override suspend fun update(account: Account) {
        if (failWrites) error("disk full")
        store[account.id] = account
    }
    override suspend fun delete(id: AccountId) {
        if (failWrites) error("disk full")
        store.remove(id)
    }
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
    override suspend fun reorder(orderedIds: List<AccountId>) {
        if (failWrites) error("disk full")
        orderedIds.forEachIndexed { position, id ->
            store[id]?.let { store[id] = it.copy(sortOrder = position) }
        }
    }
}
