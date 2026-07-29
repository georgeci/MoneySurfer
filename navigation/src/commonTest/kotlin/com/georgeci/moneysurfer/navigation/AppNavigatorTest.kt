package com.georgeci.moneysurfer.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private fun backStack(vararg routes: Route): NavBackStack<NavKey> =
    NavBackStack<NavKey>(*routes)

/**
 * The back stack is what the user perceives as "where Back goes", and every rule here exists
 * because getting it wrong strands them: popping past the last entry leaves nothing to render, and
 * a multi-pop that stops short leaves the details screen of a row that was just deleted.
 */
class AppNavigatorTest : StringSpec({

    "push adds the route on top" {
        val stack = backStack(Route.Dashboard)

        AppNavigator(stack).push(Route.Goals)

        stack shouldContainExactly listOf(Route.Dashboard, Route.Goals)
    }

    "pop removes the top entry" {
        val stack = backStack(Route.Dashboard, Route.Goals)

        AppNavigator(stack).pop()

        stack shouldContainExactly listOf(Route.Dashboard)
    }

    "popping the last entry leaves the stack empty rather than failing" {
        val stack = backStack(Route.Dashboard)

        AppNavigator(stack).pop()

        stack.shouldBeEmptyStack()
    }

    "a multi-pop removes exactly that many entries" {
        val stack = backStack(
            Route.Dashboard,
            Route.Goals,
            Route.GoalDetails("g-1"),
            Route.GoalEdit("g-1"),
        )

        AppNavigator(stack).pop(count = 2)

        stack shouldContainExactly listOf(Route.Dashboard, Route.Goals)
    }

    // Deleting a transaction from its edit screen pops both the edit and the details underneath.
    // Asking for more than there is must stop at the last entry: an empty stack renders nothing.
    "a multi-pop never empties the stack" {
        val stack = backStack(Route.Dashboard, Route.Goals)

        AppNavigator(stack).pop(count = 5)

        stack shouldContainExactly listOf(Route.Dashboard)
    }

    "replaceTop swaps the current destination without growing the stack" {
        val stack = backStack(Route.Dashboard, Route.GoalCreation)

        AppNavigator(stack).replaceTop(Route.GoalDetails("g-1"))

        stack shouldContainExactly listOf(Route.Dashboard, Route.GoalDetails("g-1"))
    }

    // Sign-out and the launch decision both land here: whatever the user was looking at must not
    // be reachable with Back afterwards.
    "resetTo clears everything below the new root" {
        val stack = backStack(Route.Dashboard, Route.Goals, Route.GoalDetails("g-1"))

        AppNavigator(stack).resetTo(Route.SignIn)

        stack shouldContainExactly listOf(Route.SignIn)
    }

    "resetTo bootstraps an empty stack" {
        val stack = backStack()

        AppNavigator(stack).resetTo(Route.Onboarding)

        stack shouldContainExactly listOf(Route.Onboarding)
    }
})

private fun NavBackStack<NavKey>.shouldBeEmptyStack() {
    size shouldBe 0
}
