package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.dynamicTheme
import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.Bookshelf
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.collectionsBookmark
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.buttonTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

@Serializable
enum class BooksTab { Books, Authors, Series }

data class FilterOption(val id: String, val label: String, val filterFn: (AudioBook) -> Boolean)

@Routable("/library")
class LibraryPage : Page {
    override val title get() = Constant("Library")

    @QueryParameter
    val currentTab = Signal(BooksTab.Books)


    @QueryParameter
    val bookSearchQuery = Signal("")
    @QueryParameter
    val bookSelectedFilter = Signal<FilterOption?>(null)
    @QueryParameter
    val bookTypeFilter = Signal<ItemType?>(null) // null = All

    @QueryParameter
    val authorSearchQuery = Signal("")

    @QueryParameter
    val seriesSearchQuery = Signal("")



    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {

        col {
            // Tab buttons
            paddingByEdge = Edges(0.rem,1.rem,0.rem,0.rem)
            row {
                paddingByEdge = Edges(1.rem,0.rem,1.rem,0.rem)
                expanding.buttonTheme.button {
                    centered.text("Books")
                    onClick { currentTab.set(BooksTab.Books) }
                    dynamicTheme {
                        if (currentTab() == BooksTab.Books) ImportantSemantic else null
                    }
                }

                expanding.buttonTheme.button {
                    centered.text("Authors")
                    onClick { currentTab.set(BooksTab.Authors) }
                    dynamicTheme {
                        if (currentTab() == BooksTab.Authors) ImportantSemantic else null
                    }
                }

                expanding.buttonTheme.button {
                    centered.text("Series")
                    onClick { currentTab.set(BooksTab.Series) }
                    dynamicTheme {
                        if (currentTab() == BooksTab.Series) ImportantSemantic else null
                    }
                }
            }
            expanding.swapView{
                swapping(
                    current = {
                        currentTab()
                    },
                    views = {currentTab ->
                        when(currentTab) {
                            BooksTab.Books -> with(BooksPage(bookSearchQuery,bookSelectedFilter,bookTypeFilter)) { render() }
                            BooksTab.Authors -> with(AuthorsPage(authorSearchQuery)) { render() }
                            BooksTab.Series -> with(SeriesPage(seriesSearchQuery)) { render() }
                        }

                    }
                )
            }


        }
    }
}
