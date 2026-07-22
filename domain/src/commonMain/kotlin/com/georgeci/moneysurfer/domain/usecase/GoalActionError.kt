package com.georgeci.moneysurfer.domain.usecase

sealed interface GoalActionError {
    data object GoalNotFound : GoalActionError
    data object WorkspaceNotFound : GoalActionError

    /** The goal's currency is not the workspace base currency — v1 supports no other. */
    data object CurrencyMismatch : GoalActionError

    /** Target must be a positive amount. */
    data object InvalidTarget : GoalActionError

    /** A contribution of zero moves nothing and is rejected. */
    data object InvalidAmount : GoalActionError

    /** A withdrawal cannot take the saved amount below zero. */
    data object WithdrawalBelowZero : GoalActionError

    /** PAUSED and ARCHIVED goals take no new money. */
    data object GoalNotAcceptingContributions : GoalActionError

    /** COMPLETED is derived from progress and cannot be set by hand. */
    data object StatusNotSettable : GoalActionError

    data class LocalWriteFailed(val cause: Throwable) : GoalActionError
}
