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
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.playback.SleepTimerMode
import com.kf7mxe.inglenook.screens.*
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
import com.lightningkite.kiteui.views.forEachById
import com.lightningkite.kiteui.views.forEachUpdating
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

        OuterSemantic.onNext.coordinatorFrame {
            applySafeInsets(bottom = false)
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

            col {
                gap = 0.5.rem
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

                // Main content area with coordinator frame for bottom sheet
                MainContentSemantic.onNext.expanding.navigatorView(navigator)

                // Now playing bottom sheet - only show when something is playing
                // and not on setup page
//                    shownWhen {
//                        val currentPage = mainPageNavigator.currentPage()
//                        val hasBook = PlaybackState.currentBook() != null
//                        hasBook && currentPage !is JellyfinSetupPage
//                    }.
                //            shownWhen { openNowPlaying() }.
//                    nowPlaying(BottomSheetState.PARTIALLY_EXPANDED)


                // Bottom navigation bar
                beforeNextElementSetup {
                    applySafeInsets(top = false)
                }.shownWhen {
                    val currentPage = mainPageNavigator.currentPage()
                    currentPage !is JellyfinSetupPage && !AppState.softInputOpen() && currentPage !is FullScreen
                }.bottomBar(mainNavPages)


            }
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
    // Mini player row (collapsed view)
    row {
        expanding.button {
            expanding.row {
                // Thumbnail
                sizeConstraints(width = 3.rem, height = 3.rem).frame {
                    image {
                        ::source {
                            val client = jellyfinClient()

                            PlaybackState.currentBook()?.let { book ->
                                book.coverImageId?.let { coverImageId ->
                                    client?.getImageUrl(coverImageId, book.id)?.let { ImageRemote(it) }
                                }
                            }
                        }
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
                        ::content { PlaybackState.currentBook()?.authors?.joinToString(", ") ?: "" }
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
        nowPlaying()
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

    card.frame {
        // Blurred background layer (only when enabled in theme settings)
        blurredImage(PlaybackState.currentBook, rememberSuspending {
            persistedThemeSettings().showPlayingBookCoverOnNowPlayingAndBookDetail
        })

        // Content layer
        scrolling.col {
//                applySafeInsets(top = false, bottom = true)
            gap = 0.0.rem
            padding = 0.rem

            col {
                padding = 1.rem
                gap = 1.5.rem

                // Large cover image
                centered.sizeConstraints(maxWidth = 16.rem, maxHeight = 16.rem).frame {
                    sizeConstraints(maxWidth = 16.rem, maxHeight = 16.rem).image {
                        rView::shown{
                            PlaybackState.currentBook() != null
                        }
                        ::source {
                            val client = jellyfinClient()
                            println("DEBUG PlaybackState.currentBook()?.coverImageId ${PlaybackState.currentBook()?.coverImageId}")

                            PlaybackState.currentBook()?.let { book ->
                                book.coverImageId?.let { coverImageId ->
                                    client?.getImageUrl(coverImageId, book.id)?.let { ImageRemote(it) }
                                }
                            }
                        }
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
                    gap = 0.25.rem
                    centered.h2 {
                        ::content { PlaybackState.currentBook()?.title ?: "No book playing" }
                    }
                    centered.subtext {
                        ::content { PlaybackState.currentBook()?.authors?.joinToString(", ") ?: "" }
                    }
                }

                // Current chapter info
//                centered.text {
//                    ::content {
//                        PlaybackState.currentChapter()?.name ?: ""
//                    }
//                }


                // Current chapter name (above seek bar)

                val showChapters = Signal(false)


                button {
                    centered.text {
                        ::content {

                            PlaybackState.currentChapter()?.name ?: "${chapters().size} Chapters"
                        }
                    }
//                    atEnd.icon {
//                        ::source { if (showChapters()) Icon.unfoldLess else Icon.unfoldMore }
//                        description = "Toggle chapters"
//                    }
                    onClick {
                        dialog { dismiss ->
                            card.sizeConstraints(width = 30.rem).scrolling.col {

                                    // Get chapters once for rendering
                                    forEachUpdating(chapters) { chapter ->
                                        val index = remember {

                                            chapters().indexOf(chapter())
                                        }
                                        val currentIndex = remember {
                                            println("DEBUG index() ${index()}")
                                            println("DEBUG PlaybackState.currentChapter() ${PlaybackState.currentChapter() != null}")
                                            if (chapters().indexOf(PlaybackState.currentChapter()) == index()) index() else -1
                                        }


                                        val isCurrent = remember {
                                            println("DEBUG index ${index()}")
                                            println("DEBUG currentIndex() ${currentIndex()}")
                                            index() == currentIndex()
                                        }
                                        val isPast = remember {
                                            index() < currentIndex()
                                        }

                                        button {
                                            row {
                                                gap = 0.5.rem
                                                padding = 0.5.rem

                                                // Chapter number
                                                centered.subtext {
                                                    ::content {
                                                        "${index() + 1}"
                                                    }
                                                }

                                                // Chapter name
                                                expanding.col {
                                                    gap = 0.rem
                                                    text {
                                                        ::content {
                                                            chapter().name
                                                        }
                                                        ellipsis = true
                                                    }
                                                    // Show start time
                                                    subtext {

                                                        ::content {
                                                            val totalSeconds = chapter().startPositionTicks / 10_000_000
                                                            val hours = totalSeconds / 3600
                                                            val minutes = (totalSeconds % 3600) / 60
                                                            val seconds = totalSeconds % 60
                                                            if (hours > 0) {
                                                                "$hours:${
                                                                    minutes.toString().padStart(2, '0')
                                                                }:${seconds.toString().padStart(2, '0')}"
                                                            } else {
                                                                "$minutes:${seconds.toString().padStart(2, '0')}"
                                                            }
                                                        }
                                                    }
                                                }

                                                // Current indicator
                                                icon {
                                                    ::shown {
                                                        isCurrent()
                                                    }
                                                    source = Icon.playArrow
                                                }

                                            }

                                            dynamicTheme {
                                                when {
                                                    isCurrent() -> SelectedSemantic

                                                    isPast() -> ThemeDerivation {
                                                        it.copy(
                                                            id = "past-chapter",
                                                            foreground = it.foreground.closestColor().applyAlpha(0.6f)
                                                        ).withBack

                                                    }

                                                    else -> null
                                                }
                                            }

                                            onClick {
                                                PlaybackState.seek(chapter.invoke().startPositionTicks)
                                            }
                                        }
                                    }
                                }
                                atBottom.card.button {
                                    centered.text("Cancel")
                                    onClick {
                                        dismiss()
                                    }
                                }


//                    for ((index, chapter) in chapters.withIndex()) {
//                        val currentChapter = PlaybackState.currentChapter.value
//                        val currentIndex =
//                            if (currentChapter != null) chapters.indexOf(currentChapter) else -1
//                        val isCurrent = index == currentIndex
//                        val isPast = index < currentIndex
//
//                        button {
//                            row {
//                                gap = 0.5.rem
//                                padding = 0.5.rem
//
//                                // Chapter number
//                                subtext { content = "${index + 1}" }
//
//                                // Chapter name
//                                expanding.col {
//                                    gap = 0.rem
//                                    text {
//                                        content = chapter.name
//                                        ellipsis = true
//                                    }
//                                    // Show start time
//                                    subtext {
//                                        val totalSeconds = chapter.startPositionTicks / 10_000_000
//                                        val hours = totalSeconds / 3600
//                                        val minutes = (totalSeconds % 3600) / 60
//                                        val seconds = totalSeconds % 60
//                                        content = if (hours > 0) {
//                                            "$hours:${
//                                                minutes.toString().padStart(2, '0')
//                                            }:${seconds.toString().padStart(2, '0')}"
//                                        } else {
//                                            "$minutes:${seconds.toString().padStart(2, '0')}"
//                                        }
//                                    }
//                                }
//
//                                // Current indicator
//                                if (isCurrent) {
//                                    icon(Icon.playArrow, "Playing")
//                                }
//                            }
//
//                            onClick {
//                                PlaybackState.seek(chapter.startPositionTicks)
//                            }
//
//                            if (isCurrent) {
//                                themeChoice += SelectedSemantic
//                            }
//                            if (isPast) {
//                                themeChoice += ThemeDerivation {
//                                    it.copy(
//                                        id = "past-chapter",
//                                        foreground = it.foreground.closestColor().applyAlpha(0.6f)
//                                    ).withBack
//                                }
//                            }
//                        }
//
//                        if (index < chapters.size - 1) {
//                            separator()
//                        }
//                    }
                        }
                    }
                }
            }

            shownWhen { chapters()?.isNotEmpty() == true }.col {
                gap = 0.5.rem

                // Chapter list header (clickable to expand)

                // Chapter list (expandable)


                // Playback controls
                PlaybackControls(compact = false)

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
                    gap = 0.5.rem

                    // Show current timer status if active
                    shownWhen { PlaybackState.sleepTimerMode() != null }.centered.row {
                        gap = 0.5.rem
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
                        gap = 0.5.rem
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
                        gap = 0.5.rem
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
}

interface FullScreen