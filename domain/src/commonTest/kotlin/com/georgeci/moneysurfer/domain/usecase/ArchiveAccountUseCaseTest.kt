package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ArchiveAccountUseCaseTest : StringSpec({

    "archive flips archived flag" {
        val target = anAccount(id = accountId("a-1"))
        val repo = FakeAccountRepository(mutableMapOf(target.id to target))
        val result = ArchiveAccountUseCase(repo).invoke(target.id)
        result shouldBe Either.Right(Unit)
        repo.store[target.id]?.archived shouldBe true
    }

    "archive on missing account returns AccountNotFound" {
        val repo = FakeAccountRepository(mutableMapOf())
        val result = ArchiveAccountUseCase(repo).invoke(accountId("missing"))
        result.shouldBeInstanceOf<Either.Left<AccountActionError>>()
        result.value shouldBe AccountActionError.AccountNotFound
    }

    "restore flips archived flag back" {
        val target = anAccount(id = accountId("a-1"), archived = true)
        val repo = FakeAccountRepository(mutableMapOf(target.id to target))
        val result = RestoreAccountUseCase(repo).invoke(target.id)
        result shouldBe Either.Right(Unit)
        repo.store[target.id]?.archived shouldBe false
    }

    "restore on already-active account is a no-op success" {
        val target = anAccount(id = accountId("a-1"), archived = false)
        val repo = FakeAccountRepository(mutableMapOf(target.id to target))
        val result = RestoreAccountUseCase(repo).invoke(target.id)
        result shouldBe Either.Right(Unit)
        repo.setArchivedCalls shouldBe 0
    }

    "archive on already-archived account is a no-op success" {
        val target = anAccount(id = accountId("a-1"), archived = true)
        val repo = FakeAccountRepository(mutableMapOf(target.id to target))
        val result = ArchiveAccountUseCase(repo).invoke(target.id)
        result shouldBe Either.Right(Unit)
        repo.setArchivedCalls shouldBe 0
    }
})

private class FakeAccountRepository(
    val store: MutableMap<AccountId, Account>,
) : AccountRepository {
    var setArchivedCalls = 0
    override fun getAll(): Flow<List<Account>> = flowOf(store.values.toList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(emptyList())
    override suspend fun getById(id: AccountId): Account? = store[id]
    override suspend fun insert(account: Account) { store[account.id] = account }
    override suspend fun update(account: Account) { store[account.id] = account }
    override suspend fun delete(id: AccountId) { store.remove(id) }
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        setArchivedCalls += 1
        val current = store[accountId] ?: return
        store[accountId] = current.copy(archived = archived)
    }
}
