package com.georgeci.moneysurfer.feature.insights

import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route

/**
 * One destination, and deliberately not a list/detail pair: the screen *is* the detail view of the
 * dashboard's spend widgets, so there is nothing above it to keep in a second pane.
 */
val insightsNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Insights> {
        InsightsScreen(onNavigateBack = { navigator.pop() })
    }
}
