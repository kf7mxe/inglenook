package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.card
import com.kf7mxe.inglenook.Book
import com.kf7mxe.inglenook.ItemType
import com.kf7mxe.inglenook.ThemePreset
import com.kf7mxe.inglenook.appTheme
import com.kf7mxe.inglenook.book
import com.kf7mxe.inglenook.pause
import com.kf7mxe.inglenook.persistedThemePreset
import com.kf7mxe.inglenook.playArrow
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.screens.openEbook
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.storage.BackgroundSetToSpecificColor
import com.kf7mxe.inglenook.util.RgbColor
import com.kf7mxe.inglenook.util.extractDominantColors
import com.kf7mxe.inglenook.util.loadResizedImagePixels
import com.kf7mxe.inglenook.util.mostProminentColor
import com.lightningkite.kiteui.views.atBottomCenter
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.rememberSuspending

private val dominantColorCache = mutableMapOf<String, RgbColor?>()

fun ViewWriter.featuredBookCard(
    book: Reactive<Book>,
    onClick: suspend () -> Unit
) {
    col {
        val coverDominantColor = rememberSuspending {
            getDominantColor(book())
        }
        centered.col {
            dynamicTheme {
                getSemanticForBookBackground(coverDominantColor(),appTheme().background.closestColor(), CardSemantic)
            }
            sizeConstraints(width = 25.rem, height = 25.rem).button {
                centered.coverImage(
                    imageId = { book().coverImageId },
                    itemId = { book().id },
                    fallbackIcon = Icon.book,
                    imageHeight = 20.rem,
                    scaleType = ImageScaleType.Fit
                )

                atBottomCenter.button {
                    themeChoice += ImportantSemantic
                    centered.row {
                        icon {
                            ::source {
                                if (book().itemType == ItemType.Ebook) Icon.book.copy(3.5.rem, 3.5.rem)
                                else if (PlaybackState.currentBook()?.id == book().id && PlaybackState.isPlaying()) Icon.pause.copy(
                                    3.5.rem,
                                    3.5.rem
                                )
                                else Icon.playArrow.copy(3.5.rem, 3.5.rem)
                            }
                            ::description { if (book().itemType == ItemType.Ebook) "Ebook" else "Play" }
                        }
                    }
                    onClick {
                        val currentBook = book.invoke()
                        if (book().itemType == ItemType.Ebook)
                            openEbook(currentBook.id, this@button)
                        else {
                            if (currentBook == PlaybackState.currentBook && PlaybackState.isPlaying()) {
                                println("DEBUG bookcard pause")
                                PlaybackState.pause()
                            } else {
                                println("DEBUG play bookCard")
                                val startPosition = currentBook.userData?.playbackPositionTicks ?: 0
                                PlaybackState.play(currentBook, startPosition)
                            }
                        }
                    }
                }
                onClick{onClick()}
            }
            button {
                col {
                    text {
                        ::content { book().title }
                        ellipsis = true
                        lineClamp = 1
                    }
                    subtext {
                        ::content {
                            book().authors.takeIf { it.isNotEmpty() }?.map { it.name }?.joinToString(", ")
                                ?: "Unknown Author"
                        }
                        ellipsis = true
                        lineClamp = 1
                    }
                    bookStatusIndicator(book)
                }
                this.onClick { onClick() }
            }
        }
    }
}

suspend fun getDominantColor(book: Book): RgbColor?{
    if (!persistedThemePreset().showBookBackgroundColor) return null
    val coverImageId = book.coverImageId ?: return null
    if (book.id in dominantColorCache) return dominantColorCache[book.id]
    val imageUrl = jellyfinClient()?.getImageUrl(coverImageId, book.id)
        ?: return null
    try {
        val cachedImage = ImageCache.get(imageUrl) ?: return null
        val imageData = loadResizedImagePixels(cachedImage, 32, 32)
            ?: return null
        val colors = extractDominantColors(imageData.pixels, imageData.width, imageData.height, 3)
        return mostProminentColor(colors).also { dominantColorCache[book.id] = it }
    } catch (e: Exception) {
        dominantColorCache[book.id] = null
       return null
    }
}

 fun getSemanticForBookBackground(coverDominantColor: RgbColor?,bgColor:Color,defaultThemeDerivation: Semantic): Semantic {
    val dominantRgb = coverDominantColor ?: return defaultThemeDerivation
    val bgColorInt = bgColor.toInt()
    val bgR = ((bgColorInt ushr 16) and 0xFF) / 255f
    val bgG = ((bgColorInt ushr 8) and 0xFF) / 255f
    val bgB = (bgColorInt and 0xFF) / 255f
    val mixedRgb = RgbColor(
        dominantRgb.r * 0.30f + bgR * 0.70f,
        dominantRgb.g * 0.30f + bgG * 0.70f,
        dominantRgb.b * 0.30f + bgB * 0.70f
    )
    val mixedColor = Color.fromHexString(mixedRgb.toHexString())


     return BackgroundSetToSpecificColor[mixedColor]
//    return ThemeDerivation { t -> t.copy(id = "book-tint-${mixedColor.toInt()}", background = mixedColor).withBack }
}

fun ViewWriter.bookCard(
    book: Reactive<Book>,
    onClick: suspend () -> Unit
) {
    val coverDominantColor = rememberSuspending {
        getDominantColor(book())
    }

    centered.sizeConstraints(width = 15.rem, height = 24.rem).col {
        dynamicTheme {
            getSemanticForBookBackground(coverDominantColor(),appTheme().background.closestColor(), CardSemantic)
        }
        // Cover image with click
        button {
//            padding = 0.rem
            col {
                centered.frame {
                    coverImage(
                        imageId = { book().coverImageId },
                        itemId = { book().id },
                        fallbackIcon = Icon.book,
                        imageHeight = 12.rem,
                        scaleType = ImageScaleType.Fit
                    )
                }
            }
            this.onClick { onClick() }
        }
        row {
            sizeConstraints(width = 10.rem).button {
                col {
                    text {
                        ::content { book().title }
                        ellipsis = true
                        lineClamp = 2
                    }
                    subtext {
                        ::content {
                            book().authors.takeIf { it.isNotEmpty() }?.map { it.name }?.joinToString(",")
                                ?: "Unknown Author"
                        }
                        ellipsis = true
                        lineClamp = 2
                    }
                    bookStatusIndicator(book)
                }
                this.onClick { onClick() }
            }
            col {
                centered.button {
                    themeChoice += ImportantSemantic
                    centered.icon {
                        ::source {
                            if (book().itemType == ItemType.Ebook) Icon.book else {
                                if (PlaybackState.currentBook()?.id == book().id && PlaybackState.isPlaying()) {
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
                        if (book().itemType == ItemType.Ebook)
                            openEbook(currentBook.id, this@button)
                        else {
                            if (currentBook == PlaybackState.currentBook() && PlaybackState.isPlaying()) {
                                PlaybackState.pause()
                            } else {
                                val startPosition = currentBook.userData?.playbackPositionTicks ?: 0
                                PlaybackState.play(currentBook, startPosition)
                            }
                        }
                    }
                }
            }
        }
    }
}
