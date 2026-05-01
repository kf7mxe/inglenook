package com.kf7mxe.inglenook.components

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.playback.SleepTimerMode
import com.kf7mxe.inglenook.screens.AuthorDetailPage
import com.kf7mxe.inglenook.screens.BookCoverFullscreenPage
import com.kf7mxe.inglenook.screens.BookDetailPage
import com.kf7mxe.inglenook.storage.DangerSemantic
import com.kf7mxe.inglenook.storage.ImageSemantic
import com.kf7mxe.inglenook.storage.NowPlayingSemantic
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.atBottom
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.navigation.dialogPageNavigator
import com.lightningkite.kiteui.views.closePopovers
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending

fun ViewWriter.nowPlayingPreview() {
    val cachedPreviewCover = rememberSuspending {
        val book = PlaybackState.currentBook()
        jellyfinClient().fetchCoverImage(book?.coverImageId, book?.id)
    }

    // Mini player row (collapsed view)
    row {
        expanding.button {
            expanding.row {
                // Thumbnail
                sizeConstraints(width = 3.rem, height = 3.rem).frame {
                    themed(ImageSemantic).image {
                        ::source { cachedPreviewCover() }
                        scaleType = ImageScaleType.Crop
                    }
                    centered.icon {
                        ::shown {
                            PlaybackState.currentBook() == null
                        }
                        source = Icon.book
                    }

                }

                // Title and author
                expanding.col {
                    gap = 0.25.rem
                    text {
                        ::content { PlaybackState.currentBook()?.title ?: "" }
                        ellipsis = true
                    }
                    subtext {
                        ::content { PlaybackState.currentBook()?.authors?.map { it.name }?.joinToString(", ") ?: "" }
                        ellipsis = true
                    }
                    sizeConstraints(height = 0.5.rem).progressBar {
                        ::shown { PlaybackState.duration() > 0L }
                        ::ratio {
                            val duration = PlaybackState.duration()
                            if (duration > 0) (PlaybackState.positionTicks().toFloat() / duration).coerceIn(0f, 1f)
                            else 0f
                        }
                    }
                }
            }
            onClick {
                if (com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Web) {
                    dialog {
                        nowPlaying()
                    }

                } else {
                    nowPlayingBottomSheet()
                }
            }
        }

        button {
            centered.icon(Icon.reverseThirtySeconds, "Skip back")
            onClick { PlaybackState.skipBackward() }
        }

        // Play/Pause button
        button {
            centered.col {
                shownWhen { PlaybackState.isBuffering() }.centered.row {
                    sizeConstraints(width = 1.5.rem, height = 1.5.rem).activityIndicator {  }
                }
                shownWhen { !PlaybackState.isBuffering() }.centered.icon {
                    ::source { if (PlaybackState.isPlaying()) Icon.pause else Icon.playArrow }
                    ::description { if (PlaybackState.isPlaying()) "Pause" else "Play" }
                }
            }
            onClick {
                if (PlaybackState.isPlaying()) {
                    PlaybackState.pause()
                } else {
                    PlaybackState.resume()
                }
            }
        }
    }
}

fun ViewWriter.nowPlayingBottomSheet() {
    coordinatorFrame?.bottomSheet(
        partialRatio = 0.75f,
        startState = BottomSheetState.PARTIALLY_EXPANDED,
        blockBehind = true
    ) { control ->
        unpadded.nowPlaying() {
            control.close()
        }
    }
}


fun ViewWriter.nowPlaying(onClose: () -> Unit = {}) {

    val chapters = rememberSuspending {
        val client = jellyfinClient()
        PlaybackState.currentBook()?.itemType
        PlaybackState.currentBook()?.let { book ->
            if (book.itemType != ItemType.AudioBook) return@let emptyList()
            client?.getAudiobookChapters(book.id)
        }?.map {
            Chapter(
                name = it.Name,
                startPositionTicks = it.StartPositionTicks,
                imageId = null
            )
        } ?: emptyList()
    }

    NowPlayingSemantic.onNext.frame {
        // Blurred background layer (only when enabled in theme settings)

        val coverDominantColor = rememberSuspending {
            PlaybackState.currentBook()?.let{book->
                getDominantColor(book)
            }
        }
        dynamicTheme {
            getSemanticForBookBackground(coverDominantColor(),appTheme().background.closestColor(),
                NowPlayingSemantic)
        }

        blurredImage(PlaybackState.currentBook, rememberSuspending {
            persistedThemeSettings().showPlayingBookCoverOnNowPlayingAndBookDetail
        })

        // Content layer
        scrolling.padded.col {

            col {
                // Large cover image
                val cachedOverlayCover = rememberSuspending {
                    val book = PlaybackState.currentBook()
                    jellyfinClient().fetchCoverImage(book?.coverImageId, book?.id)
                }

                    centered.sizeConstraints(maxWidth = 16.rem, maxHeight = 16.rem).button {
                        themed(ImageSemantic).image {
                            rView::shown{
                                PlaybackState.currentBook() != null
                            }
                            ::source { cachedOverlayCover() }
                            scaleType = ImageScaleType.Fit
                        }
                        centered.icon {
                            ::shown {
                                PlaybackState.currentBook() == null
                            }
                            source = Icon.book.copy(width = 8.rem, height = 8.rem)
                        }
                        onClick {
                            PlaybackState.currentBook()?.let { book ->
                                mainPageNavigator.navigate(BookCoverFullscreenPage(book.id, book.coverImageId))
                                onClose()
                            }

                        }
                    }

                // Title and author
                centered.col {
                    button {
                        centered.h2 {
                            ::content { PlaybackState.currentBook()?.title ?: "No book playing" }
                        }
                        onClick {
                            PlaybackState.currentBook()?.let {
                                mainPageNavigator.navigate(BookDetailPage(it.id))
                                onClose()
                            }
                        }
                    }
                    col {
                        forEach(remember { PlaybackState.currentBook()?.authors ?: listOf() }) { author ->
                            button {
                                centered.subtext {
                                    ::content {
                                        PlaybackState.currentBook()?.authors?.map { it.name }?.joinToString(", ") ?: ""
                                    }
                                }
                                onClick {
                                    mainPageNavigator.navigate(AuthorDetailPage(author.id))
                                    onClose()
                                }
                            }
                        }
                    }
                }

                unpadded.button {
                    ::shown {
                        chapters().isNotEmpty()
                    }
                    centered.text {
                        ::content {

                            PlaybackState.currentChapter()?.name ?: "${chapters().size} Chapters"
                        }
                    }
                    onClick {
                        coordinatorFrame?.bottomSheet { control ->
                            col {
                                themed(DialogSemantic).sizeConstraints(width = 30.rem).chaptersList(chapters) { chapter ->
                                    PlaybackState.seek(chapter.startPositionTicks)
                                    control.close()
                                }
                                atBottom.card.button {
                                    centered.text("Cancel")
                                    onClick {
                                        control.close()

                                    }
                                }

                            }
                        }
                    }
                }
            }


            // Playback controls
            playbackControls()

            // Playback speed selector
            centered.row {
                gap = 0.5.rem
                centered.text("Speed:")
                for (speed in listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) {
                    centered.button {
                        text("${speed}x")
                        onClick { PlaybackState.setPlaybackSpeed(speed) }
                        dynamicTheme {
                            if (PlaybackState.playbackSpeed() == speed) ImportantSemantic else null
                        }
                    }
                }
            }

            // Sleep timer section
            col {

                // Show current timer status if active
                shownWhen { PlaybackState.sleepTimerMode() != null }.centered.row {
                    centered.icon(Icon.timer, "Sleep timer active")
                    centered.text {
                        ::content {
                            when (PlaybackState.sleepTimerMode()) {
                                is SleepTimerMode.Minutes -> {
                                    val remaining = PlaybackState.sleepTimerMinutesRemaining() ?: 0
                                    "Sleep in ${remaining}m"
                                }

                                SleepTimerMode.EndOfChapter -> "Sleep at end of chapter"
                                null -> ""
                            }
                        }
                    }
                    button {
                        icon(Icon.close, "Cancel timer")
                        onClick { PlaybackState.cancelSleepTimer() }
                    }
                }

                // Timer options
                centered.row {
                    centered.text("Sleep:")
                    for (minutes in listOf(15, 30, 45, 60)) {
                        button {
                            text("${minutes}m")
                            onClick { PlaybackState.setSleepTimer(SleepTimerMode.Minutes(minutes)) }
                            dynamicTheme {
                                val mode = PlaybackState.sleepTimerMode()
                                if (mode is SleepTimerMode.Minutes && mode.minutes == minutes) ImportantSemantic else null
                            }
                        }
                    }
                    button {
                        text("End chapter")
                        onClick { PlaybackState.setSleepTimer(SleepTimerMode.EndOfChapter) }
                        dynamicTheme {
                            if (PlaybackState.sleepTimerMode() == SleepTimerMode.EndOfChapter) ImportantSemantic else null
                        }
                    }
                }
            }


            // Stop button
            centered.button {
                row {
                    icon(Icon.stop, "Stop")
                    text("Stop Playback")
                }
                onClick { PlaybackState.stop() }
                themeChoice += DangerSemantic
            }

        }

        onRemove {
            isNowPlayingOpen.value = false
        }
    }
}
