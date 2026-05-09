package com.georgeci.moneysurfer.feature.settings.about

import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AboutViewModel(
    appInfo: AppInfo,
) : MviViewModel<AboutState, AboutEvent, AboutEffect>(
    initialState = AboutState(appVersion = appInfo.version),
) {

    override fun onEvent(event: AboutEvent) {
        when (event) {
            AboutEvent.OnBackClick -> postSideEffect(AboutEffect.NavigateBack)

            AboutEvent.OnTermsClick -> postSideEffect(AboutEffect.OpenUrl(URL_TERMS))
            AboutEvent.OnPrivacyClick -> postSideEffect(AboutEffect.OpenUrl(URL_PRIVACY))
            AboutEvent.OnLicensesClick -> postSideEffect(AboutEffect.NavigateToLicenses)
            AboutEvent.OnHelpCenterClick -> postSideEffect(AboutEffect.OpenUrl(URL_HELP))
            AboutEvent.OnContactClick -> postSideEffect(AboutEffect.OpenEmail(EMAIL_CONTACT))
            AboutEvent.OnRateClick -> postSideEffect(AboutEffect.OpenStoreListing)
            AboutEvent.OnRegionClick -> postSideEffect(AboutEffect.OpenRegionPicker)
            AboutEvent.OnDiagnosticClick -> postSideEffect(AboutEffect.NavigateToDiagnostic)
            AboutEvent.OnGitHubClick -> postSideEffect(AboutEffect.OpenUrl(URL_GITHUB))
        }
    }

    private companion object {
        const val URL_TERMS = "https://moneysurfer.app/legal/terms"
        const val URL_PRIVACY = "https://moneysurfer.app/legal/privacy"
        const val URL_HELP = "https://moneysurfer.app/help"
        const val URL_GITHUB = "https://github.com/georgeci/MoneySurfer2026"
        const val EMAIL_CONTACT = "support@moneysurfer.app"
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
