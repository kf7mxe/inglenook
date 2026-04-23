package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.dynamicTheme
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.buttonTheme
import com.lightningkite.kiteui.views.card
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.Constant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi

@Serializable
enum class BooksTab { Books, Authors, Series }

data class FilterOption(val id: String, val label: String, val filterFn: (Book) -> Boolean)

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
            paddingByEdge = Edges(1.rem,1.rem,1.rem,0.rem)
            row {
                expanding.card.button {
                    centered.text("Books")
                    onClick { currentTab.set(BooksTab.Books) }
                    dynamicTheme {
                        if (currentTab() == BooksTab.Books) ImportantSemantic else null
                    }
                }

                expanding.card.button {
                    centered.text("Authors")
                    onClick { currentTab.set(BooksTab.Authors) }
                    dynamicTheme {
                        if (currentTab() == BooksTab.Authors) ImportantSemantic else null
                    }
                }

                expanding.card.button {
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
                            BooksTab.Authors -> with(AuthorsPage(authorSearchQuery, bookTypeFilter)) { render() }
                            BooksTab.Series -> with(SeriesPage(seriesSearchQuery)) { render() }
                        }

                    }
                )
            }


        }
    }
}
