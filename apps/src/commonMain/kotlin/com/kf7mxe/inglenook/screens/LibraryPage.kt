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
import com.kf7mxe.inglenook.ViewMode
import com.kf7mxe.inglenook.collectionsBookmark
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

enum class BooksTab { Books, Authors }

data class FilterOption(val id: String, val label: String, val filterFn: (AudioBook) -> Boolean)

@Routable("/library")
class LibraryPage : Page {
    override val title get() = Constant("Library")

    @OptIn(ExperimentalUuidApi::class)
    override fun ViewWriter.render() {
        val currentTab = Signal(BooksTab.Books)

        col {
            gap = 0.rem

            // Tab buttons
            row {
                padding = 1.rem
                gap = 0.5.rem

                expanding.button {
                    centered.text("Books")
                    onClick { currentTab.set(BooksTab.Books) }
                    dynamicTheme {
                        if (currentTab() == BooksTab.Books) ImportantSemantic else null
                    }
                }

                expanding.button {
                    centered.text("Authors")
                    onClick { currentTab.set(BooksTab.Authors) }
                    dynamicTheme {
                        if (currentTab() == BooksTab.Authors) ImportantSemantic else null
                    }
                }
            }

            separator()

            expanding.swapView{
                swapping(
                    current = {
                        currentTab()
                    },
                    views = {currentTab ->
                        when(currentTab) {
                            BooksTab.Books -> with(BooksPage()) { render() }
                            BooksTab.Authors -> with(AuthorsPage()) { render() }
                        }

                    }
                )
            }


        }
    }
}
