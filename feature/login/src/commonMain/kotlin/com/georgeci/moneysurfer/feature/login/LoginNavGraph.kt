package com.georgeci.moneysurfer.feature.login

import com.georgeci.moneysurfer.feature.login.legal.LegalScreen
import com.georgeci.moneysurfer.feature.login.onboarding.OnboardingScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route

val loginNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Onboarding> {
        OnboardingScreen(
            // `resetTo` so the welcome screen never comes back via system back.
            onNavigateToSignIn = { navigator.resetTo(Route.SignIn) },
            onNavigateToFirstAccount = { accountType ->
                navigator.resetTo(
                    Route.AccountCreation(firstRun = true, accountType = accountType.name),
                )
            },
        )
    }

    entry<Route.SignIn> {
        SignInScreen(
            onNavigateToWorkspaceSelector = {
                navigator.replaceTop(Route.WorkspaceSelector(showActions = false))
            },
            onNavigateToLegal = { navigator.push(Route.Legal) },
        )
    }

    entry<Route.Legal> {
        LegalScreen(onNavigateBack = { navigator.pop() })
    }
}
