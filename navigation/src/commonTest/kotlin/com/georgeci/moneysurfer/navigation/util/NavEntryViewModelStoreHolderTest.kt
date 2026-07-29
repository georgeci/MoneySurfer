package com.georgeci.moneysurfer.navigation.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

internal class TrackedViewModel : ViewModel() {
    var cleared: Boolean = false
        private set

    override fun onCleared() {
        cleared = true
    }
}

private fun ViewModelProvider.tracked(): TrackedViewModel = this[TrackedViewModel::class]

/**
 * One `ViewModelStore` per back-stack entry, cleared when that entry goes away. Both halves matter:
 * sharing a store across entries would hand a re-opened screen the previous one's state, and never
 * clearing leaks every view model the user has navigated through for the life of the process.
 */
class NavEntryViewModelStoreHolderTest : StringSpec({

    "the same key keeps the same store, so a recomposition does not reset the screen" {
        val holder = NavEntryViewModelStoreHolder()

        val first = holder.getOrCreate("entry-1")
        val second = holder.getOrCreate("entry-1")

        first.viewModelStore shouldBe second.viewModelStore
    }

    "different entries get different stores" {
        val holder = NavEntryViewModelStoreHolder()

        holder.getOrCreate("entry-1").viewModelStore shouldNotBe
            holder.getOrCreate("entry-2").viewModelStore
    }

    "clearing an entry clears its view models" {
        val holder = NavEntryViewModelStoreHolder()
        val viewModel = ViewModelProvider.create(holder.getOrCreate("entry-1")).tracked()

        holder.clear("entry-1")

        viewModel.cleared shouldBe true
    }

    "a key that comes back after being cleared starts from a fresh store" {
        val holder = NavEntryViewModelStoreHolder()
        val before = holder.getOrCreate("entry-1").viewModelStore

        holder.clear("entry-1")

        holder.getOrCreate("entry-1").viewModelStore shouldNotBe before
    }

    "clearing a key that was never used is a no-op" {
        NavEntryViewModelStoreHolder().clear("never-visited")
    }

    // What logout does: nothing from the previous session may survive into the next one.
    "clearAll clears every entry at once" {
        val holder = NavEntryViewModelStoreHolder()
        val first = ViewModelProvider.create(holder.getOrCreate("entry-1")).tracked()
        val second = ViewModelProvider.create(holder.getOrCreate("entry-2")).tracked()

        holder.clearAll()

        first.cleared shouldBe true
        second.cleared shouldBe true
        holder.getOrCreate("entry-1").viewModelStore shouldNotBe first
    }
})
