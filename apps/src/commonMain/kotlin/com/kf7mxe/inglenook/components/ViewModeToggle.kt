package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.dashboard
import com.kf7mxe.inglenook.viewMode
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.core.Reactive

/**
 * Reusable view mode toggle button (grid <-> list).
 */
fun ViewWriter.viewModeToggleButton() {
    button {
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
                when (mode) {
                    ViewMode.Grid -> {
                        expanding.recyclerView {
                            ::placer { RecyclerViewPlacerVerticalGrid(gridColumns) }
                            children(items, keySelector) { item ->
                                gridItem(item)
                            }
                        }
                    }
                    ViewMode.List -> {
                        expanding.recyclerView {
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
