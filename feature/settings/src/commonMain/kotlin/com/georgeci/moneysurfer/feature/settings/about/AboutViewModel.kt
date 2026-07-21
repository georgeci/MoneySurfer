package com.georgeci.moneysurfer.feature.settings.about

import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AboutViewModel(
    appInfo: AppInfo,
    offlineBuildFlags: OfflineBuildFlags,
) : MviViewModel<AboutState, AboutEvent, AboutEffect>(
    initialState = AboutState(appVersion = appInfo.version),
) {

    private val privacyUrl =
        if (offlineBuildFlags.isOffline) URL_PRIVACY_LOCAL else URL_PRIVACY

    override fun onEvent(event: AboutEvent) {
        when (event) {
            AboutEvent.OnBackClick -> postSideEffect(AboutEffect.NavigateBack)

            AboutEvent.OnTermsClick -> postSideEffect(AboutEffect.OpenUrl(URL_TERMS))
            AboutEvent.OnPrivacyClick -> postSideEffect(AboutEffect.OpenUrl(privacyUrl))
            AboutEvent.OnLicensesClick -> postSideEffect(AboutEffect.NavigateToLicenses)
            AboutEvent.OnHelpCenterClick -> postSideEffect(AboutEffect.OpenUrl(URL_HELP))
            AboutEvent.OnContactClick -> postSideEffect(AboutEffect.OpenEmail(EMAIL_CONTACT))
            AboutEvent.OnRateClick -> postSideEffect(AboutEffect.OpenStoreListing)
            AboutEvent.OnRegionClick -> postSideEffect(AboutEffect.OpenRegionPicker)
            AboutEvent.OnDiagnosticClick -> postSideEffect(AboutEffect.NavigateToDiagnostic)
            AboutEvent.OnGitHubClick -> postSideEffect(AboutEffect.OpenUrl(URL_GITHUB))
        }
    }

    // Canonical policy host: https://georgeci.github.io (georgeci.github.io repo).
    private companion object {
        const val URL_TERMS = "https://georgeci.github.io/terms.html"
        const val URL_PRIVACY = "https://georgeci.github.io/privacy-policy.html"
        const val URL_PRIVACY_LOCAL = "https://georgeci.github.io/privacy-policy-local.html"
        const val URL_HELP = "https://github.com/georgeci/MoneySurfer#readme"
        const val URL_GITHUB = "https://github.com/georgeci/MoneySurfer"
        const val EMAIL_CONTACT = "georgeci007+moneysurfer@gmail.com"
    }
}

data class AboutState(
    val appVersion: String = "1.0.0",
)

sealed interface AboutEvent {
    data object OnBackClick : AboutEvent
    data object OnTermsClick : AboutEvent
    data object OnPrivacyClick : AboutEvent
    data object OnLicensesClick : AboutEvent
    data object OnHelpCenterClick : AboutEvent
    data object OnContactClick : AboutEvent
    data object OnRateClick : AboutEvent
    data object OnRegionClick : AboutEvent
    data object OnDiagnosticClick : AboutEvent
    data object OnGitHubClick : AboutEvent
}

sealed interface AboutEffect {
    data object NavigateBack : AboutEffect
    data class OpenUrl(val url: String) : AboutEffect
    data class OpenEmail(val address: String) : AboutEffect
    data object OpenStoreListing : AboutEffect
    data object OpenRegionPicker : AboutEffect
    data object NavigateToLicenses : AboutEffect
    data object NavigateToDiagnostic : AboutEffect
}
