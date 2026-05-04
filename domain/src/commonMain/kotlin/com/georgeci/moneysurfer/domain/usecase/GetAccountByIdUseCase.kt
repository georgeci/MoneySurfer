package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import org.koin.core.annotation.Single

@Single
class GetAccountByIdUseCase(
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(id: AccountId): Account? = accountRepository.getById(id)
}
