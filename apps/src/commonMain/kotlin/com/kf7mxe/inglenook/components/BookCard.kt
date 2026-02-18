package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.pause
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.screens.EbookReaderPage
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.context.invoke

fun ViewWriter.BookCard(
    book: Reactive<Book>,
    onPlayClick: (suspend (Book)-> Unit)? = null,
    onClick: suspend () -> Unit
) {
    val cachedCover = rememberSuspending {
        val client = jellyfinClient()
        val bookData = book()
        if (client != null && bookData.coverImageId != null) {
            ImageCache.get(client.getImageUrl(bookData.coverImageId, bookData.id))
        } else null
    }

    centered.card.sizeConstraints(width = 14.rem, height = 22.rem).col {
        // Play button overlay at bottom right
        button {
            padding = 0.rem
            col {
                // Cover image with play button overlay
                centered.frame {
                    // Cover image
                    themed(ImageSemantic).sizeConstraints( height = 12.rem).centered.image {
                        this.rView::shown {
                            book().coverImageId != null
                        }
                        ::source { cachedCover() }
                        scaleType = ImageScaleType.Fit
                    }
                    centered.sizeConstraints( height = 12.rem).icon {
                        ::shown {
                            book().coverImageId == null
                        }
                        source = Icon.book
                        ::description { book().title }
                    }
                }


            }
            this.onClick { onClick() }
        }
        row {
            expanding.button {
                // Title and author (clickable to go to detail)
                col {
                    text {
                        ::content { book().title }
                        ellipsis = true
                        lineClamp = 2
                    }
                    subtext {
                        ::content { book().authors.takeIf { it.isNotEmpty() }?.map{it.name}?.joinToString(",") ?: "Unknown Author" }
                        ellipsis = true
                        lineClamp = 2
                    }
                }
                this.onClick { onClick() }
            }
            col {
                // Only show play button for audiobooks, show book icon for ebooks
                centered.button {
                    themeChoice += ImportantSemantic
                    // Show play icon for audiobooks, book icon for ebooks
                    centered.icon {
                        ::source {
                            if (book().itemType == ItemType.Ebook)  Icon.book else {
                                if( PlaybackState.currentBook() == book() && PlaybackState.isPlaying()) {
                                    Icon.pause
                                } else {
                                    Icon.playArrow

                                }
                            }
                        }
                        ::description { if (book().itemType == ItemType.Ebook) "Ebook" else "Play" }
                    }
                    onClick {
                        val currentBook = book.invoke()
                        // Only play audiobooks
                        if (currentBook.itemType == ItemType.AudioBook) {
                            if (onPlayClick != null) {
                                onPlayClick(currentBook)
                            } else {
                                val startPosition = currentBook.userData?.playbackPositionTicks ?: 0L
                                PlaybackState.play(currentBook, startPosition)
                            }
                        }
                        if(currentBook.itemType == ItemType.Ebook) {
                            mainPageNavigator.navigate(EbookReaderPage(currentBook.id))
                        }
                        // For ebooks, clicking will just go to detail page (handled by onClick on card)
                    }
                }
            }
        }
    }
}