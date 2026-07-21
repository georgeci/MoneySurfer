package com.georgeci.moneysurfer.feature.settings.account

import com.georgeci.moneysurfer.domain.auth.AccountDeletionError
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.usecase.DeleteUserAccountUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

/**
 * User-account deletion flow (issue #213), online builds only — the Settings entry point is
 * hidden offline. Email/password sessions must retype their password (doubles as the Firebase
 * re-auth credential); anonymous sessions go straight to the confirmation dialog.
 *
 * On success no navigation is posted: the use case clears the session user pointer and
 * AppLaunchViewModel bounces the whole app to SignIn.
 */
@KoinViewModel
class DeleteUserAccountViewModel(
    private val deleteUserAccount: DeleteUserAccountUseCase,
    authRemoteRepository: AuthRemoteRepository,
) : MviViewModel<DeleteUserAccountState, DeleteUserAccountEvent, DeleteUserAccountEffect>(
    initialState = DeleteUserAccountState(
        isAnonymousUser = authRemoteRepository.isCurrentUserAnonymous(),
        userEmail = authRemoteRepository.currentEmail(),
    ),
) {

    override fun onEvent(event: DeleteUserAccountEvent) {
        when (event) {
            DeleteUserAccountEvent.OnBackClick -> postSideEffect(DeleteUserAccountEffect.NavigateBack)
            is DeleteUserAccountEvent.OnPasswordChange ->
                updateState { copy(password = event.value, passwordMissing = false) }
            DeleteUserAccountEvent.OnDeleteClick -> requestConfirmation()
            DeleteUserAccountEvent.OnConfirmDismissed ->
                updateState { copy(showConfirmDialog = false) }
            DeleteUserAccountEvent.OnConfirmAccepted -> deleteAccount()
        }
    }

    private fun requestConfirmation() {
        val state = currentState
        if (!state.isAnonymousUser && state.password.isBlank()) {
            updateState { copy(passwordMissing = true) }
            return
        }
        updateState { copy(showConfirmDialog = true) }
    }

    private fun deleteAccount() {
        updateState { copy(showConfirmDialog = false, isDeleting = true) }
        launch(
            onError = {
                updateState { copy(isDeleting = false) }
                postSideEffect(DeleteUserAccountEffect.Notify(DeleteUserAccountNotice.Generic))
            },
        ) {
            val password = currentState.password.takeIf { !currentState.isAnonymousUser }
            deleteUserAccount(password = password).fold(
                ifLeft = { error ->
                    updateState { copy(isDeleting = false) }
                    postSideEffect(DeleteUserAccountEffect.Notify(error.toNotice()))
                },
                ifRight = {
                    // Session user pointer is already null — AppLaunchViewModel navigates
                    // to SignIn; keep the overlay up so the screen can't be re-submitted.
                },
            )
        }
    }

    private fun AccountDeletionError.toNotice(): DeleteUserAccountNotice = when (this) {
        AccountDeletionError.NotSignedIn -> DeleteUserAccountNotice.Generic
        AccountDeletionError.InvalidCredentials -> DeleteUserAccountNotice.WrongPassword
        is AccountDeletionError.ReauthenticationFailed -> DeleteUserAccountNotice.Generic
        AccountDeletionError.RequiresRecentLogin -> DeleteUserAccountNotice.RequiresRecentLogin
        is AccountDeletionError.RemoteDataCleanupFailed -> DeleteUserAccountNotice.CleanupFailed
        is AccountDeletionError.AuthDeletionFailed -> DeleteUserAccountNotice.AuthDeletionFailed
    }
}

data class DeleteUserAccountState(
    val isAnonymousUser: Boolean = false,
    val userEmail: String? = null,
    val password: String = "",
    val passwordMissing: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val showPasswordField: Boolean get() = !isAnonymousUser
}

sealed interface DeleteUserAccountEvent {
    data object OnBackClick : DeleteUserAccountEvent
    data class OnPasswordChange(val value: String) : DeleteUserAccountEvent
    data object OnDeleteClick : DeleteUserAccountEvent
    data object OnConfirmDismissed : DeleteUserAccountEvent
    data object OnConfirmAccepted : DeleteUserAccountEvent
}

/** Localised in the screen layer — the ViewModel stays free of platform string APIs. */
enum class DeleteUserAccountNotice {
    WrongPassword,
    RequiresRecentLogin,
    CleanupFailed,
    AuthDeletionFailed,
    Generic,
}

sealed interface DeleteUserAccountEffect {
    data object NavigateBack : DeleteUserAccountEffect
    data class Notify(val notice: DeleteUserAccountNotice) : DeleteUserAccountEffect
}
