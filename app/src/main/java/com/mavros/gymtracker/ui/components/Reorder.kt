package com.mavros.gymtracker.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val DRAG_LONG_PRESS_MS = 400L
private const val EDGE_PX = 140f
private const val MAX_AUTO_SCROLL_PER_FRAME = 28f

/**
 * Drag-to-reorder state for a list. Items report their heights via [reorderItem]; while an item is
 * dragged, [offsetFor] gives its visual offset and [targetIndex] the slot its centre is over.
 */
class ReorderState {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    private var dragDelta by mutableFloatStateOf(0f)
    private var autoScroll by mutableFloatStateOf(0f)
    private val heights = mutableStateMapOf<Int, Int>()

    fun setHeight(index: Int, height: Int) {
        heights[index] = height
    }

    fun start(index: Int) {
        draggingIndex = index
        dragDelta = 0f
        autoScroll = 0f
    }

    fun drag(deltaY: Float) {
        dragDelta += deltaY
    }

    fun addAutoScroll(px: Float) {
        autoScroll += px
    }

    fun stop() {
        draggingIndex = null
        dragDelta = 0f
        autoScroll = 0f
    }

    fun offsetFor(index: Int): Float = if (draggingIndex == index) dragDelta + autoScroll else 0f

    /** The index the dragged item would land on, or -1 if nothing is being dragged. */
    fun targetIndex(count: Int): Int {
        val from = draggingIndex ?: return -1
        val itemHeights = IntArray(count) { heights[it] ?: 0 }
        val tops = IntArray(count)
        var acc = 0
        for (i in 0 until count) {
            tops[i] = acc
            acc += itemHeights[i]
        }
        val draggedCenter = tops[from] + dragDelta + autoScroll + itemHeights[from] / 2f
        var target = 0
        for (i in 0 until count) {
            if (i != from && tops[i] + itemHeights[i] / 2f < draggedCenter) target++
        }
        return target
    }
}

@Composable
fun rememberReorderState(key: Any?): ReorderState = remember(key) { ReorderState() }

fun Modifier.reorderItem(state: ReorderState, index: Int): Modifier =
    onSizeChanged { state.setHeight(index, it.height) }
        .zIndex(if (state.draggingIndex == index) 1f else 0f)
        .graphicsLayer { translationY = state.offsetFor(index) }

/**
 * Scrolls [listState] while the dragged pointer is within [EDGE_PX] of the viewport's top or bottom
 * edge, faster the closer it gets. Set [viewportTop]/[viewportBottom] in window coordinates.
 */
class DragAutoScroller(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    var viewportTop = 0f
    var viewportBottom = 0f
    private var pointerY = 0f
    private var onScrolled: ((Float) -> Unit)? = null
    private var job: Job? = null

    fun start(pointerYWindow: Float, onScrolled: (Float) -> Unit) {
        pointerY = pointerYWindow
        this.onScrolled = onScrolled
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val dy = when {
                    pointerY < viewportTop + EDGE_PX ->
                        -((viewportTop + EDGE_PX - pointerY).coerceAtMost(EDGE_PX) / EDGE_PX) * MAX_AUTO_SCROLL_PER_FRAME
                    pointerY > viewportBottom - EDGE_PX ->
                        ((pointerY - (viewportBottom - EDGE_PX)).coerceAtMost(EDGE_PX) / EDGE_PX) * MAX_AUTO_SCROLL_PER_FRAME
                    else -> 0f
                }
                if (dy != 0f) onScrolled(listState.scrollBy(dy))
                withFrameNanos { }
            }
        }
    }

    fun update(pointerYWindow: Float) {
        pointerY = pointerYWindow
    }

    fun stop() {
        onScrolled = null
        job?.cancel()
        job = null
    }
}

/** Shortens the long-press timeout so dragging starts sooner. */
private class LongPressViewConfiguration(
    base: ViewConfiguration,
    private val longPressMs: Long,
) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = longPressMs
}

/** A drag handle: long-press and drag it to move item [index] within a list of [count] items. */
@Composable
fun DragHandle(
    state: ReorderState,
    index: Int,
    count: Int,
    autoScroller: DragAutoScroller,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var handleTopInWindow by remember { mutableFloatStateOf(0f) }
    val currentOnMove by rememberUpdatedState(onMove)
    val base = LocalViewConfiguration.current
    val longPressConfig = remember(base) { LongPressViewConfiguration(base, DRAG_LONG_PRESS_MS) }
    CompositionLocalProvider(LocalViewConfiguration provides longPressConfig) {
        Icon(
            Icons.Rounded.DragIndicator,
            contentDescription = "Drag to reorder",
            modifier = modifier
                .onGloballyPositioned { handleTopInWindow = it.positionInWindow().y }
                .pointerInput(state, autoScroller, index, count) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            state.start(index)
                            autoScroller.start(handleTopInWindow + offset.y) { state.addAutoScroll(it) }
                        },
                        onDragEnd = {
                            val from = state.draggingIndex
                            if (from != null) {
                                val to = state.targetIndex(count)
                                if (to in 0 until count && to != from) currentOnMove(from, to)
                            }
                            state.stop()
                            autoScroller.stop()
                        },
                        onDragCancel = {
                            state.stop()
                            autoScroller.stop()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            state.drag(amount.y)
                            autoScroller.update(handleTopInWindow + change.position.y)
                        },
                    )
                }
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .size(20.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}
