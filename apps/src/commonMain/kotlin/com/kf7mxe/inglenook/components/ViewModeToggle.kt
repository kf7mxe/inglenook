package com.kf7mxe.inglenook.components

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
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.Recycler2
import com.lightningkite.kiteui.views.l2.RecyclerViewPlacerVerticalGrid
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Reactive
import kotlinx.coroutines.delay
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

suspend fun <T : Any> Recycler2.restoreLastViewedItem(
    items: suspend () -> List<T>,
    itemId: (T) -> String,
    isReady: suspend () -> Boolean = { true },
    isLoading: suspend () -> Boolean = { false },
    hasMore: suspend () -> Boolean = { false },
    loadedCount: suspend () -> Int = { items().size },
    loadMore: (suspend () -> Unit)? = null,
    targetExistsOutsideItems: suspend (String) -> Boolean = { false }
) {
    val targetId = lastItemViewedScrollToOnBack() ?: return

    while (!isReady() || isLoading()) {
        delay(25)
    }

    while (true) {
        while (isLoading()) {
            delay(25)
        }

        val currentItems = items()
        val index = currentItems.indexOfFirst { itemId(it) == targetId }
        if (index >= 0) {
            scrollToIndex(index, Align.Start, false)
            lastItemViewedScrollToOnBack.set(null)
            return
        }

        if (targetExistsOutsideItems(targetId) || !hasMore() || loadMore == null) {
            lastItemViewedScrollToOnBack.set(null)
            return
        }

        val countBeforeLoad = loadedCount()
        loadMore()
        if (hasMore() && loadedCount() == countBeforeLoad && !isLoading()) {
            lastItemViewedScrollToOnBack.set(null)
            return
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
    listItem: ViewWriter.(Reactive<T>) -> Unit,
    onNearEnd: (suspend () -> Unit)? = null,
    restoreReady: suspend () -> Boolean = { true },
    restoreIsLoading: suspend () -> Boolean = { false },
    restoreHasMore: suspend () -> Boolean = { false },
    restoreLoadedCount: suspend () -> Int = { items().size },
    restoreTargetExistsOutsideItems: suspend (String) -> Boolean = { false },
    restorePosition: Boolean = true
) {

    expanding.swapView {
        swapping(
            current = { viewMode() },
            views = { mode ->
                when (mode) {
                    ViewMode.Grid -> {
                        expanding.recyclerView {
                            ::placer { RecyclerViewPlacerVerticalGrid(gridColumns) }
                            launch {
                                if (restorePosition) {
                                    restoreLastViewedItem(
                                        items = { items() },
                                        itemId = { keySelector(it).toString() },
                                        isReady = restoreReady,
                                        isLoading = restoreIsLoading,
                                        hasMore = restoreHasMore,
                                        loadedCount = restoreLoadedCount,
                                        loadMore = onNearEnd,
                                        targetExistsOutsideItems = restoreTargetExistsOutsideItems
                                    )
                                }
                            }
                            children(items, keySelector) { item ->
                                gridItem(item)
                            }
                            reactive {
                                val currentItems = items()
                                val currentLastIndex = lastIndex()
                                if (onNearEnd != null && currentItems.isNotEmpty() && currentLastIndex >= currentItems.lastIndex - 10) {
                                    launch { onNearEnd() }
                                }
                            }
                        }
                    }

                    ViewMode.List -> {
                        expanding.recyclerView {
                            launch {
                                if (restorePosition) {
                                    restoreLastViewedItem(
                                        items = { items() },
                                        itemId = { keySelector(it).toString() },
                                        isReady = restoreReady,
                                        isLoading = restoreIsLoading,
                                        hasMore = restoreHasMore,
                                        loadedCount = restoreLoadedCount,
                                        loadMore = onNearEnd,
                                        targetExistsOutsideItems = restoreTargetExistsOutsideItems
                                    )
                                }
                            }
                            children(items, keySelector) { item ->
                                listItem(item)
                            }
                            reactive {
                                val currentItems = items()
                                val currentLastIndex = lastIndex()
                                if (onNearEnd != null && currentItems.isNotEmpty() && currentLastIndex >= currentItems.lastIndex - 10) {
                                    launch { onNearEnd() }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

