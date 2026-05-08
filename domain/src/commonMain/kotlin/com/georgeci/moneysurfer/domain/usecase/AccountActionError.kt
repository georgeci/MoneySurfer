package com.georgeci.moneysurfer.domain.usecase

sealed interface AccountActionError {
    data object AccountNotFound : AccountActionError
    data class LocalWriteFailed(val cause: Throwable) : AccountActionError
}
