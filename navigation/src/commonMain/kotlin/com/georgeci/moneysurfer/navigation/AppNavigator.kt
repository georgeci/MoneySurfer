package com.georgeci.moneysurfer.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

class AppNavigator internal constructor(
    private val backStack: NavBackStack<NavKey>,
) {
    fun push(route: Route) {
        backStack.add(route)
    }

    fun pop() {
        backStack.removeLastOrNull()
    }

    fun replaceTop(route: Route) {
        backStack.removeLastOrNull()
        backStack.add(route)
    }

    fun resetTo(route: Route) {
        while (backStack.removeLastOrNull() != null) {
            // clear backstack before bootstrapping target route
        }
        backStack.add(route)
    }
}
