package com.georgeci.moneysurfer.navigation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntryDecorator

class NavEntryViewModelStoreHolder {

    private val stores = mutableMapOf<Any, ViewModelStore>()

    fun getOrCreate(key: Any): ViewModelStoreOwner {
        val store = stores.getOrPut(key) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun clear(key: Any) {
        stores.remove(key)?.clear()
    }

    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

@Composable
fun <T : Any> rememberViewModelStoreNavEntryDecorator(
    vmStoreHolder: NavEntryViewModelStoreHolder = remember { NavEntryViewModelStoreHolder() },
): NavEntryDecorator<T> {
    return remember(vmStoreHolder) {
        ViewModelStoreNavEntryDecorator(vmStoreHolder)
    }
}

class ViewModelStoreNavEntryDecorator<T : Any>(
    vmStoreHolder: NavEntryViewModelStoreHolder,
) : NavEntryDecorator<T>(
    decorate = { entry ->
        val owner = remember(entry.contentKey) { vmStoreHolder.getOrCreate(entry.contentKey) }

        DisposableEffect(entry.contentKey) {
            onDispose { vmStoreHolder.clear(entry.contentKey) }
        }

        CompositionLocalProvider(
            LocalViewModelStoreOwner provides owner,
        ) {
            entry.Content()
        }
    },
)
