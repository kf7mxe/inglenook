package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.HasId
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.lastItemViewedScrollToOnBack
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch

/**
 * Reusable view mode toggle button (grid <-> list).
 */
fun ViewWriter.viewModeToggleButton() {
    card.button {
        icon {
            ::source { if (viewMode() == ViewMode.Grid) Icon.menu else Icon.dashboard }
            description = "Toggle view"
        }
        onClick {
            viewMode.value = if (viewMode.value == ViewMode.Grid) ViewMode.List else ViewMode.Grid
        }
    }
}

/**
 * Reusable grid/list swap view. Switches between a grid RecyclerView and a list RecyclerView
 * based on the current viewMode.
 */
fun <T : Any> ViewWriter.gridListView(
    items: Reactive<List<T>>,
    keySelector: (T) -> Any,
    gridColumns: Int = 2,
    gridItem: ViewWriter.(Reactive<T>) -> Unit,
    listItem: ViewWriter.(Reactive<T>) -> Unit
) {
    expanding.swapView {
        swapping(
            current = { viewMode() },
            views = { mode ->
                val scrollTo = remember {
                    if(lastItemViewedScrollToOnBack() == null) return@remember 0
                    items().indexOfFirst {(it as HasId) .id == lastItemViewedScrollToOnBack() }.takeIf { it != -1 } ?: return@remember 0
                }

                when (mode) {
                    ViewMode.Grid -> {
                        expanding.recyclerView {
                            ::placer { RecyclerViewPlacerVerticalGrid(gridColumns) }
                            launch {
                                scrollToIndex(scrollTo(), Align.Center,false)
                            }
                            children(items, keySelector) { item ->
                                gridItem(item)
                            }
                        }
                    }
                    ViewMode.List -> {
                        expanding.recyclerView {
                            launch {
                                scrollToIndex(scrollTo(), Align.Center,false)
                            }
                            children(items, keySelector) { item ->
                                listItem(item)
                            }
                        }
                    }
                }
            }
        )
    }
}

