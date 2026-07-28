package com.georgeci.moneysurfer.navigation

import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The width → presentation policy and the invariants the one destination list has to hold for all
 * three presentations to be driven from it (issue #389).
 */
class NavigationShellTest : StringSpec({

    "compact width presents no navigation suite" {
        // Not an accident of a `>= Medium` comparison — the deliberate G6 answer. If this ever
        // flips, it is a product decision and this assertion is where it gets recorded.
        SurferWindowSize.Compact.navigationPresentation() shouldBe NavigationPresentation.None
    }

    "medium width presents the rail" {
        SurferWindowSize.Medium.navigationPresentation() shouldBe NavigationPresentation.Rail
    }

    "expanded and large widths present the drawer" {
        SurferWindowSize.Expanded.navigationPresentation() shouldBe NavigationPresentation.Drawer
        SurferWindowSize.Large.navigationPresentation() shouldBe NavigationPresentation.Drawer
    }

    "a destination matches its own route and no other" {
        TopLevelDestination.entries.forEach { destination ->
            TopLevelDestination.entries.forEach { other ->
                destination.matches(other.route) shouldBe (destination == other)
            }
        }
    }

    "no destination matches an absent top-level route" {
        TopLevelDestination.entries.forEach { destination ->
            destination.matches(null) shouldBe false
        }
    }

    "sections are declared as contiguous blocks" {
        // The drawer renders one block per section in encounter order, so a section split across
        // two runs of the enum would silently render as two captioned groups.
        val runs = TopLevelDestination.entries
            .map { it.section }
            .fold(emptyList<NavigationSection>()) { acc, section ->
                if (acc.lastOrNull() == section) acc else acc + section
            }
        runs shouldContainExactly listOf(NavigationSection.Primary, NavigationSection.Manage)
    }

    "only the manage section is captioned" {
        NavigationSection.Primary.label shouldBe null
        NavigationSection.Manage.label shouldNotBe null
    }

    "no two destinations share a route" {
        // Two entries on the same route would both light up in the drawer, and `resetTo` could
        // only ever land on one of them.
        val routes = TopLevelDestination.entries.map { it.route }
        routes.distinct().size shouldBe routes.size
    }
})
