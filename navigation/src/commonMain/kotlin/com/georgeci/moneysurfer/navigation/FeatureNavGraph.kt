package com.georgeci.moneysurfer.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

typealias FeatureNavGraph = EntryProviderScope<NavKey>.(navigator: AppNavigator) -> Unit
