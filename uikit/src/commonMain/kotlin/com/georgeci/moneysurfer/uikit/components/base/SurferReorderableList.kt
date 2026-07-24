package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder state for a vertical `LazyColumn`.
 *
 * Moves are reported as *keys* — the row being dragged and the row it landed on — never as list
 * indices. A reorderable section usually shares its list with headers and with rows that must stay
 * put, so a `LazyColumn` index is not a position in the reordered collection; keys skip that
 * translation entirely. Only keys named in `keys` are drop targets, which is what keeps a header
 * from swallowing a drag.
 *
 * Create it with [rememberSurferReorderState], then apply [surferReorderableItem] to every
 * reorderable row and [surferReorderHandle] to the [SurferDragHandle] inside it.
 */
@Stable
class SurferReorderState internal constructor(
    private val listState: LazyListState,
    private val reorderableKeys: State<List<Any>>,
    private val onMove: State<(from: Any, to: Any) -> Unit>,
) {

    /** Key of the row under the finger, or null when nothing is being dragged. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    private var draggingIndex: Int? = null
    private var dragStartOffset: Int = 0
    private var draggedDistance by mutableFloatStateOf(0f)

    private val draggingItem: LazyListItemInfo?
        get() = draggingIndex?.let { index ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        }

    /**
     * Vertical translation for the dragged row: where the finger has taken it, minus where the
     * list has since laid it out. Reordering moves the row out from under the finger, and without
     * this correction it would jump a full row height on every swap.
     */
    val draggingItemOffset: Float
        get() = draggingItem?.let { dragStartOffset + draggedDistance - it.offset } ?: 0f

    fun isDragging(key: Any): Boolean = draggingKey == key

    internal fun onDragStart(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        draggingIndex = info.index
        dragStartOffset = info.offset
        draggedDistance = 0f
    }

    internal fun onDrag(delta: Float) {
        draggedDistance += delta
        // A move updates draggingIndex straight away, but the list only re-lays-out on the next
        // frame. Pointer events can arrive faster than that, and acting on the stale layout would
        // read a neighbour's key as the dragged row and reorder the wrong widget. The key check
        // skips those ticks until the two agree again; the distance above is banked either way.
        val dragged = draggingItem?.takeIf { it.key == draggingKey } ?: return
        val target = dropTargetFor(dragged) ?: return
        onMove.value(dragged.key, target.key)
        draggingIndex = target.index
    }

    /**
     * The row whose slot the dragged one should take: the first reorderable neighbour whose bounds
     * contain the dragged row's midpoint — where the finger has put it, not where it is laid out.
     */
    private fun dropTargetFor(dragged: LazyListItemInfo): LazyListItemInfo? {
        val center = (dragStartOffset + draggedDistance + dragged.size / 2f).toInt()
        return listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != dragged.index &&
                candidate.key in reorderableKeys.value &&
                center in candidate.offset..(candidate.offset + candidate.size)
        }
    }

    internal fun onDragStop() {
        draggingKey = null
        draggingIndex = null
        draggedDistance = 0f
    }
}

/**
 * Remembers the reorder state for [listState]. [keys] are the keys of the reorderable rows in
 * their current order — pass them fresh on every recomposition; the state reads the latest value
 * rather than the one captured when it was created. [onMove] receives the dragged row's key and
 * the key of the row whose slot it should take.
 */
@Composable
fun rememberSurferReorderState(
    listState: LazyListState,
    keys: List<Any>,
    onMove: (from: Any, to: Any) -> Unit,
): SurferReorderState {
    val currentKeys = rememberUpdatedState(keys)
    val currentOnMove = rememberUpdatedState(onMove)
    return remember(listState) { SurferReorderState(listState, currentKeys, currentOnMove) }
}

/**
 * Modifier for a reorderable row: the dragged one follows the finger above its neighbours, the
 * rest animate into the slots it vacates.
 */
@Composable
fun LazyItemScope.surferReorderableItem(state: SurferReorderState, key: Any): Modifier =
    if (state.isDragging(key)) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = state.draggingItemOffset }
    } else {
        Modifier.animateItem()
    }

/**
 * Modifier for the grip of the row identified by [key]. The handle is the only drag target, so the
 * list still scrolls when the user drags anywhere else on a row.
 */
fun Modifier.surferReorderHandle(state: SurferReorderState, key: Any): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.onDragStart(key) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragStop() },
            onDragCancel = { state.onDragStop() },
        )
    }
