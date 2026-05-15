package com.georgeci.moneysurfer.feature.login

import com.georgeci.moneysurfer.feature.login.legal.LegalScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route

val loginNavGraph: FeatureNavGraph = { navigator ->
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
