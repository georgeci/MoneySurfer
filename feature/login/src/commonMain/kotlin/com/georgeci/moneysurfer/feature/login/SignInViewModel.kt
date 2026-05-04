package com.georgeci.moneysurfer.feature.login

import arrow.core.Either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.usecase.AnonymousLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.LoginUseCase
import com.georgeci.moneysurfer.domain.usecase.SignupUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SignInViewModel(
    private val login: LoginUseCase,
    private val signup: SignupUseCase,
    private val anonymousLogin: AnonymousLoginUseCase,
    private val demoLogin: DemoLoginUseCase,
) : MviViewModel<SignInState, SignInEvent, SignInEffect>(
    initialState = SignInState(),
) {

    private val log = Logger.withTag(TAG)

    override fun onEvent(event: SignInEvent) {
        when (event) {
            is SignInEvent.OnEmailChanged ->
                updateState { copy(email = event.email, error = null) }
            is SignInEvent.OnPasswordChanged ->
                updateState { copy(password = event.password, error = null) }
            SignInEvent.OnToggleModeClick -> updateState {
                copy(
                    mode = when (mode) {
                        AuthMode.SignIn -> AuthMode.SignUp
                        AuthMode.SignUp -> AuthMode.SignIn
                    },
                    error = null,
                )
            }
            SignInEvent.OnSubmitClick -> submit()
            SignInEvent.OnAnonymousLoginClick -> runAuth("anon") { anonymousLogin() }
            SignInEvent.OnLoginClick -> runAuth("demo") { demoLogin() }
        }
    }

    private fun submit() {
        val state = currentState
        if (state.isLoading) return
        if (!state.canSubmit) {
            log.i { "[submit] rejected: missing credentials (mode=${state.mode})" }
            updateState { copy(error = SignInError.MissingCredentials) }
            return
        }
        val email = state.email
        val password = state.password
        val label = when (state.mode) {
            AuthMode.SignIn -> "login"
            AuthMode.SignUp -> "signup"
        }
        runAuth(label) {
            when (state.mode) {
                AuthMode.SignIn -> login(email, password)
                AuthMode.SignUp -> signup(email, password)
            }
        }
    }

    private fun runAuth(label: String, block: suspend () -> Either<AuthError, *>) {
        if (currentState.isLoading) {
            log.d { "[$label] ignored: already loading" }
            return
        }
        log.i { "[$label] start" }
        launch(
            onError = { err ->
                log.w(err) { "[$label] failed with exception" }
                updateState { copy(isLoading = false, error = SignInError.Unknown) }
            },
        ) {
            updateState { copy(isLoading = true, error = null) }
            block().fold(
                ifLeft = { err ->
                    val mapped = err.toSignInError()
                    log.w(err.cause) { "[$label] failed -> $mapped (${err.message})" }
                    updateState { copy(isLoading = false, error = mapped) }
                },
                ifRight = {
                    log.i { "[$label] ok -> navigate" }
                    postSideEffect(SignInEffect.NavigateToWorkspaceSelector)
                    updateState { copy(isLoading = false) }
                },
            )
        }
    }

    private companion object {
        const val TAG = "SignInVM"
    }
}

private fun AuthError.toSignInError(): SignInError = when (type) {
    AuthError.Type.WeakPassword -> SignInError.WeakPassword
    AuthError.Type.EmailAlreadyInUse -> SignInError.EmailAlreadyInUse
    AuthError.Type.InvalidCredentials -> SignInError.InvalidCredentials
    AuthError.Type.PermissionDenied -> SignInError.PermissionDenied
    AuthError.Type.Unknown -> SignInError.Unknown
}

enum class AuthMode { SignIn, SignUp }

enum class SignInError {
    MissingCredentials,
    InvalidCredentials,
    EmailAlreadyInUse,
    WeakPassword,
    PermissionDenied,
    Unknown,
}

data class SignInState(
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.SignIn,
    val isLoading: Boolean = false,
    val error: SignInError? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= 6 && !isLoading
}

sealed interface SignInEvent {
    data class OnEmailChanged(val email: String) : SignInEvent
    data class OnPasswordChanged(val password: String) : SignInEvent
    data object OnToggleModeClick : SignInEvent
    data object OnSubmitClick : SignInEvent
    data object OnLoginClick : SignInEvent
    data object OnAnonymousLoginClick : SignInEvent
}

sealed interface SignInEffect {
    data object NavigateToWorkspaceSelector : SignInEffect
}
