package com.kf7mxe.inglenook

import com.kf7mxe.inglenook.cache.blurAndCacheImage
import com.kf7mxe.inglenook.cache.getBlurredCachedImage
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.bar
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.appBase
import com.lightningkite.kiteui.views.l2.applySafeInsets
import com.lightningkite.kiteui.views.l2.coordinatorFrame
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.l2.navigatorView
import com.kf7mxe.inglenook.components.PlaybackControls
import com.kf7mxe.inglenook.components.blurredImage
import com.kf7mxe.inglenook.components.connectivityDialog
import com.kf7mxe.inglenook.components.offlineBanner
import com.kf7mxe.inglenook.cache.ImageCache
import com.kf7mxe.inglenook.components.chaptersList
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.playback.SleepTimerMode
import com.kf7mxe.inglenook.screens.*
import com.kf7mxe.inglenook.storage.NowPlayingSemantic
import com.kf7mxe.inglenook.storage.readImageFromStorage
import com.kf7mxe.inglenook.theming.createTheme
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.navigation.bindToPlatform
import com.lightningkite.kiteui.navigation.dialogPageNavigator
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.RView
import com.lightningkite.kiteui.views.atBottom
import com.lightningkite.kiteui.views.atEnd
import com.lightningkite.kiteui.views.closePopovers
import com.lightningkite.kiteui.views.closeThisPopover
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.forEachById
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.overlayFrame
import com.lightningkite.kiteui.views.nav
import com.lightningkite.kiteui.views.rContextAddon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import io.ktor.util.Platform
import kotlinx.coroutines.launch

// Persistent theme settings - survives app restart
val persistedThemePreset = PersistentProperty<ThemePreset>("themePreset", ThemePreset.Cozy)
val persistedThemeSettings = PersistentProperty<ThemeSettings>("themeSettings", ThemeSettings())

// Initialize theme from persisted settings
val appTheme = Signal<Theme>(createTheme(persistedThemePreset.value, persistedThemeSettings.value))


val bookToShowBlurredBackgroundCoverOf = Signal<AudioBook?>(null)

// Current theme preset setting (reactive, synced with persisted)
val currentThemePreset get() = persistedThemePreset

val viewMode = Signal(ViewMode.Grid)


// View mode for book lists
enum class ViewMode {
    Grid,
    List
}

// Navigation link helper
data class NavLink(
    val title: String,
    val icon: Icon,
    val destination: () -> Page
)


val isNowPlayingOpen = Signal(false)

var ViewWriter.overlayFrame by rContextAddon<RView?>(null)
var ViewWriter.coordinatorFrame by rContextAddon<CoordinatorFrame?>(null)


fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator) {
    val mainNavPages = listOf(
        NavLink("Home", Icon.home) { DashboardPage() },
        NavLink("Library", Icon.collectionsBookmark) { LibraryPage() },
        NavLink("Bookshelf", Icon.book) { BookshelfPage() },
        NavLink("Settings", Icon.settings) { SettingsPage() },
    )

    // Restore last played book so the now-playing preview shows on relaunch
    PlaybackState.restoreLastPlayed()

    // Check if Jellyfin is configured, if not go to setup
    AppScope.launch {
        val config = jellyfinServerConfig.value
        if (config == null) {
            navigator.navigate(JellyfinSetupPage())
        } else {
            navigator.navigate(DashboardPage())
        }
    }

    appBase(navigator, dialog) {
        applySafeInsets(bottom = false)
        OuterSemantic.onNext.coordinatorFrame {

            mainPageNavigator = navigator
            dialog?.let {
                dialogPageNavigator = it
            }
            navigator.bindToPlatform(context)
            pageNavigator = navigator
            overlayFrame = this
            coordinatorFrame = this


            val wallpaper = rememberSuspending {
                persistedThemeSettings().wallpaperPath?.let { wallpaperPath ->
                    println("DEBUG wfirst let ${wallpaperPath}")
                    val parentDir = wallpaperPath.split("/").first()
                    val fileName = wallpaperPath.split("/").last()

                    val cachedFileName = "${persistedThemeSettings().wallpaperBlurRadius}-${fileName}"

                    getBlurredCachedImage(cachedFileName) ?: readImageFromStorage(
                        parentDir,
                        fileName
                    )?.let { unblurredImage ->
                        println("DEBUG unblurredImage ${unblurredImage}")
                        blurAndCacheImage(
                            cachedFileName,
                            wallpaperPath,
                            unblurredImage,
                            persistedThemeSettings().wallpaperBlurRadius,
                            appTheme().background,
                            0.75f
                        )
                    }
                }
            }
//
            image {
                scaleType = ImageScaleType.Crop
                ::source { wallpaper() }
                rView::shown {
                    println("DEBUG wallpaper() != null ${wallpaper() != null}")
                    wallpaper() != null
                }
            }
            blurredImage(PlaybackState.currentBook, remember {
                PlaybackState.currentBook() != null && persistedThemeSettings().showPlayingBookCoverAsWallpaper
            })
            blurredImage(bookToShowBlurredBackgroundCoverOf, remember {
                mainPageNavigator.currentPage() is BookDetailPage && persistedThemeSettings().showPlayingBookCoverOnNowPlayingAndBookDetail
            })

            unpadded.col {
                gap = 0.0.rem


                // Top bar with back button, title, and search
                shownWhen { mainPageNavigator.currentPage() !is FullScreen }.bar.row {
                    gap = 0.5.rem

                    button {
                        ::visible {
                            mainPageNavigator.canGoBack()
                        }
                        centered.icon {
                            source = Icon.arrowBack
                        }
                        onClick {
                            mainPageNavigator.goBack()
                        }
                    }

                    expanding.centered.h3 {
                        ::content {
                            mainPageNavigator.currentPage()?.title?.invoke() ?: ""
                        }
                    }

                    button {
                        ::visible {
                            val currentPage = mainPageNavigator.currentPage()
                            currentPage !is JellyfinSetupPage && currentPage !is SearchPage
                        }
                        centered.icon {
                            source = Icon.search
                            description = "Search"
                        }
                        onClick {
                            mainPageNavigator.navigate(SearchPage())
                        }
                    }
                }

                // Offline mode banner
                offlineBanner()

                // Main content area with coordinator frame for bottom sheet
                MainContentSemantic.onNext.expanding.navigatorView(navigator)

                // Bottom navigation bar
                beforeNextElementSetup {
                    applySafeInsets(top = false)
                }.shownWhen {
                    val currentPage = mainPageNavigator.currentPage()
                    currentPage !is JellyfinSetupPage && !AppState.softInputOpen() && currentPage !is FullScreen
                }.unpadded.bottomBar(mainNavPages)


            }

            // Connectivity dialog overlay
            var dialogShowing = false
            ConnectivityState.showingConnectivityDialog.addListener {
                val shouldShow = ConnectivityState.showingConnectivityDialog.value
                if (shouldShow && !dialogShowing) {
                    dialogShowing = true
                    dialog { dismiss ->
                        connectivityDialog {
                            dialogShowing = false
                            dismiss()
                        }
                    }
                }
            }

            // Start connectivity monitoring AFTER listener is registered
            ConnectivityState.initialize()
        }
    }
}

fun ViewWriter.bottomBar(navItems: List<NavLink>) {
    nav.col {
        shownWhen { !isNowPlayingOpen() && PlaybackState.currentBook() != null }.nowPlayingPreview()
        row {
            padding = 0.5.rem
            gap = 0.dp
            for (navLink in navItems) {
                expanding.link {
                    resetsStack = true
                    col {
                        gap = 0.0.rem
                        centered.icon {
                            source = navLink.icon.copy(width = 1.5.rem, height = 1.5.rem)
                            description = navLink.title
                        }
                        centered.subtext(navLink.title)
                    }
                    to = navLink.destination
                }
            }
        }
    }
}


fun ViewWriter.nowPlayingPreview() {
    val cachedPreviewCover = rememberSuspending {
        val client = jellyfinClient()
        val book = PlaybackState.currentBook()
        if (client != null && book?.coverImageId != null) {
            ImageCache.get(client.getImageUrl(book.coverImageId, book.id))
        } else null
    }

    // Mini player row (collapsed view)
    row {
        expanding.button {
            expanding.row {
                // Thumbnail
                sizeConstraints(width = 3.rem, height = 3.rem).frame {
                    image {
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
                }
            }
            onClick {
//                           openNowPlaying.set(true)
                if (com.lightningkite.kiteui.Platform.current == com.lightningkite.kiteui.Platform.Web) {
                    dialog {
                        nowPlaying()
                    }

                } else {
                    nowPlayingBottomSheet()
                }
            }
        }

        // Play/Pause button
        button {
            centered.icon {
                ::source { if (PlaybackState.isPlaying()) Icon.pause else Icon.playArrow }
                ::description { if (PlaybackState.isPlaying()) "Pause" else "Play" }
            }
            onClick {
                PlaybackState.togglePlayPause()
            }
        }
    }
}

fun ViewWriter.nowPlayingBottomSheet() {
    println("DEBUG coordinatorFram ${coordinatorFrame}")
    coordinatorFrame?.bottomSheet(
        partialRatio = 0.5f,
        startState = BottomSheetState.PARTIALLY_EXPANDED
    ) {
        unpadded.nowPlaying()
    }
}


fun ViewWriter.nowPlaying() {

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
        blurredImage(PlaybackState.currentBook, rememberSuspending {
            persistedThemeSettings().showPlayingBookCoverOnNowPlayingAndBookDetail
        })

        // Content layer
        scrolling.col {
//                applySafeInsets(top = false, bottom = true)

            col {
                // Large cover image
                val cachedOverlayCover = rememberSuspending {
                    val client = jellyfinClient()
                    val book = PlaybackState.currentBook()
                    if (client != null && book?.coverImageId != null) {
                        ImageCache.get(client.getImageUrl(book.coverImageId, book.id))
                    } else null
                }

                centered.sizeConstraints(maxWidth = 16.rem, maxHeight = 16.rem).frame {
                    sizeConstraints(maxWidth = 16.rem, maxHeight = 16.rem).image {
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
                                closePopovers()
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
                                }
                            }
                        }
                    }
                }

                button {
                    ::shown {
                        chapters().isNotEmpty()
                    }
                    centered.text {
                        ::content {

                            PlaybackState.currentChapter()?.name ?: "${chapters().size} Chapters"
                        }
                    }
                    onClick {
                        dialog { dismiss ->
                            sizeConstraints(width = 30.rem).chaptersList(chapters) { chapter ->
                                PlaybackState.seek(chapter.startPositionTicks)
                            }
                            atBottom.card.button {
                                centered.text("Cancel")
                                onClick {
                                    dismiss()
                                }
                            }
                        }
                    }
                }
            }


            // Playback controls
            PlaybackControls()

            // Playback speed selector
            centered.row {
                gap = 0.5.rem
                text("Speed:")
                for (speed in listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) {
                    button {
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
                    icon(Icon.timer, "Sleep timer active")
                    text {
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
                    text("Sleep:")
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
                themeChoice += ThemeDerivation { it.copy(id = "danger", foreground = Color.red).withoutBack }
            }

            // Spacer at bottom
//                space(2.0)
        }

        onRemove {
            isNowPlayingOpen.value = false
        }
    }
}

interface FullScreen