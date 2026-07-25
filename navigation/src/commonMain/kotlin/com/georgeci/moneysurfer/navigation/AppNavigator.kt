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

    /**
     * Pops up to [count] entries at once, for a screen whose caller has also become unreachable —
     * editing a transaction and deleting it leaves the details screen of a row that no longer
     * exists directly underneath.
     *
     * Never empties the stack: with nothing on it there is no destination left to render.
     */
    fun pop(count: Int) {
        repeat(count) {
            if (backStack.size <= 1) return
            backStack.removeLastOrNull()
        }
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
