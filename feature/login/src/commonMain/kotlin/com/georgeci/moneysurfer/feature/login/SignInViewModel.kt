package com.georgeci.moneysurfer.feature.login

import arrow.core.Either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.AuthError
import com.georgeci.moneysurfer.domain.usecase.AnonymousLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.LoginUseCase
import com.georgeci.moneysurfer.domain.usecase.PostAuthBootstrapUseCase
import com.georgeci.moneysurfer.domain.usecase.SignupUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SignInViewModel(
    private val login: LoginUseCase,
    private val signup: SignupUseCase,
    private val anonymousLogin: AnonymousLoginUseCase,
    private val demoLogin: DemoLoginUseCase,
    config: SignInFeatureConfig,
) : MviViewModel<SignInState, SignInEvent, SignInEffect>(
    initialState = SignInState(config = config),
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
            SignInEvent.OnTermsClick -> postSideEffect(SignInEffect.NavigateToLegal)
        }
    }

    private fun submit() {
        val state = currentState
        if (state.isLoading) return
        val invalid = state.validate()
        if (invalid != null) {
            log.i { "[submit] rejected: $invalid (mode=${state.mode})" }
            updateState { copy(error = invalid) }
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

    // `Any?` rather than a star projection: the success value is inspected, and a star-projected
    // right side is only usable as `Nothing`.
    private fun runAuth(label: String, block: suspend () -> Either<AuthError, Any?>) {
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
                    updateState {
                        copy(
                            isLoading = false,
                            error = mapped,
                            // The account already exists, so put the user one tap from the action
                            // the message advises. This is also the way out of a half-finished
                            // sign-up: `SignupUseCase` creates the Firebase user before the local
                            // and Firestore bootstrap steps, so if one of those fails the address
                            // is taken from then on and re-trying sign-up can never succeed.
                            mode = if (mapped == SignInError.EmailAlreadyInUse) AuthMode.SignIn else mode,
                        )
                    }
                },
                ifRight = { result ->
                    // Auth succeeded either way; the bootstrap just could not find the account's
                    // workspaces locally, and the selector has to say so rather than render an
                    // empty list that reads as "you have no workspaces" (issue #342).
                    val cloudDataUnavailable =
                        result is PostAuthBootstrapUseCase.Result.CloudDataUnavailable
                    log.i { "[$label] ok -> navigate (cloudDataUnavailable=$cloudDataUnavailable)" }
                    postSideEffect(SignInEffect.NavigateToWorkspaceSelector(cloudDataUnavailable))
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
    AuthError.Type.InvalidEmail -> SignInError.EmailInvalid
    AuthError.Type.InvalidCredentials -> SignInError.InvalidCredentials
    AuthError.Type.PermissionDenied -> SignInError.PermissionDenied
    // Only the account-deletion flow can produce this; a sign-in never does.
    AuthError.Type.RequiresRecentLogin -> SignInError.Unknown
    AuthError.Type.Unknown -> SignInError.Unknown
}

enum class AuthMode { SignIn, SignUp }

/** Which control an error belongs under, so the UI can anchor it to the offending input. */
enum class SignInField { Email, Password, Form }

enum class SignInError(val field: SignInField) {
    EmailRequired(SignInField.Email),
    EmailInvalid(SignInField.Email),
    EmailAlreadyInUse(SignInField.Email),
    PasswordRequired(SignInField.Password),
    PasswordTooShort(SignInField.Password),
    WeakPassword(SignInField.Password),
    InvalidCredentials(SignInField.Form),
    PermissionDenied(SignInField.Form),
    Unknown(SignInField.Form),
}

/**
 * Firebase's own floor for `createUserWithEmailAndPassword`. Enforced locally so sign-up shows a
 * concrete hint under the field instead of relying on a server round-trip.
 */
const val MIN_PASSWORD_LENGTH = 6

// Deliberately permissive — the only job is to catch typos the provider would reject anyway
// (missing @, missing dot, stray whitespace). Firebase remains the authority.
private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]{2,}$""")

data class SignInState(
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.SignIn,
    val isLoading: Boolean = false,
    val error: SignInError? = null,
    val config: SignInFeatureConfig = SignInFeatureConfig(),
) {
    /**
     * Only gated on [isLoading]. Blank or malformed input no longer disables the button — a dead
     * grey button explains nothing, whereas submitting surfaces a message under the offending
     * field (that silence was the original "I can't sign up" report).
     */
    val canSubmit: Boolean
        get() = !isLoading

    val emailError: SignInError? get() = error?.takeIf { it.field == SignInField.Email }
    val passwordError: SignInError? get() = error?.takeIf { it.field == SignInField.Password }
    val formError: SignInError? get() = error?.takeIf { it.field == SignInField.Form }

    /** First client-side problem with the current input, or null when it is worth a network call. */
    fun validate(): SignInError? = when {
        email.isBlank() -> SignInError.EmailRequired
        !EMAIL_PATTERN.matches(email.trim()) -> SignInError.EmailInvalid
        password.isEmpty() -> SignInError.PasswordRequired
        mode == AuthMode.SignUp && password.length < MIN_PASSWORD_LENGTH -> SignInError.PasswordTooShort
        else -> null
    }
}

sealed interface SignInEvent {
    data class OnEmailChanged(val email: String) : SignInEvent
    data class OnPasswordChanged(val password: String) : SignInEvent
    data object OnToggleModeClick : SignInEvent
    data object OnSubmitClick : SignInEvent
    data object OnLoginClick : SignInEvent
    data object OnAnonymousLoginClick : SignInEvent
    data object OnTermsClick : SignInEvent
}

sealed interface SignInEffect {
    data class NavigateToWorkspaceSelector(
        val cloudDataUnavailable: Boolean = false,
    ) : SignInEffect

    data object NavigateToLegal : SignInEffect
}
