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
import com.lightningkite.kiteui.views.l2.navigatorView
import com.kf7mxe.inglenook.components.blurredImage
import com.kf7mxe.inglenook.components.connectivityDialog
import com.kf7mxe.inglenook.components.nowPlayingPreview
import com.kf7mxe.inglenook.components.offlineBanner
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.screens.*
import com.kf7mxe.inglenook.storage.SelectedTab
import com.kf7mxe.inglenook.storage.UnSelectedTab
import com.kf7mxe.inglenook.storage.readImageFromStorage
import com.kf7mxe.inglenook.theming.createTheme
import com.lightningkite.kiteui.lottie.models.LottieRaw
import com.lightningkite.kiteui.lottie.models.LottieSource
import com.lightningkite.kiteui.navigation.bindToPlatform
import com.lightningkite.kiteui.navigation.dialogPageNavigator
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.RView
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.nav
import com.lightningkite.kiteui.views.rContextAddon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.kf7mxe.inglenook.util.assignThemeColors
import com.kf7mxe.inglenook.util.extractDominantColors
import com.kf7mxe.inglenook.util.loadResizedImagePixels
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.lightningkite.kiteui.lottie.views.direct.lottie

// Persistent theme settings - survives app restart
val persistedThemePreset = PersistentProperty<ThemePreset>("themePreset", ThemePreset.Cozy)
val persistedThemeSettings = PersistentProperty<ThemeSettings>("themeSettings", ThemeSettings())

// Initialize theme from persisted settings
val appTheme = Signal<Theme>(createTheme(persistedThemePreset.value, persistedThemeSettings.value))

val diagnosticsEnabled = PersistentProperty("diagnosticsEnabled", false)

val bookToShowBlurredBackgroundCoverOf = Signal<Book?>(null)

// Current theme preset setting (reactive, synced with persisted)
val currentThemePreset get() = persistedThemePreset

val viewMode = PersistentProperty("viewMode", ViewMode.Grid)


// View mode for book lists
@Serializable
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
        NavLink("Home", Icon.home) { HomePage() },
        NavLink("Library", Icon.collectionsBookmark) { LibraryPage() },
        NavLink("Bookshelf", Icon.book) { BookshelfPage() },
        NavLink("Settings", Icon.settings) { SettingsPage() },
    )

    // Restore last played book so the now-playing preview shows on relaunch
    launch {
        PlaybackState.restoreLastPlayed()
    }

    // Check if Jellyfin is configured, if not go to setup
    AppScope.launch {
        val config = jellyfinServerConfig.value
        if (config == null) {
            navigator.navigate(JellyfinSetupPage())
        } else {
            navigator.navigate(SplashPage())
        }
    }

    OuterSemantic.onNext.appBase(navigator, dialog) {
        applySafeInsets(bottom = false)
        coordinatorFrame {

            mainPageNavigator = navigator
            dialog?.let {
                dialogPageNavigator = it
            }
            navigator.bindToPlatform(context)
            pageNavigator = navigator
            overlayFrame = this
            coordinatorFrame = this


            val wallpaper = rememberSuspending {
                val settings = persistedThemeSettings()
                val preset = persistedThemePreset()
                val wallpaperPath = settings.wallpaperPath
                println("DEBUG wallpaperPath ${wallpaperPath}")
                if (wallpaperPath != null) {
                    val parentDir = wallpaperPath.split("/").first()
                    val fileName = wallpaperPath.split("/").last()
                    val cachedFileName = "${settings.wallpaperBlurRadius}-${fileName}"

                    getBlurredCachedImage(cachedFileName) ?: readImageFromStorage(
                        parentDir,
                        fileName
                    )?.let { unblurredImage ->
                        blurAndCacheImage(
                            cachedFileName,
                            wallpaperPath,
                            unblurredImage,
                            settings.wallpaperBlurRadius,
                            appTheme().background,
                            0.75f
                        )
                    }
                } else if (preset == ThemePreset.Glassish || preset == ThemePreset.Custom) {
                    // Extract colors from default wallpaper and apply to theme
                    if (settings.wallpaperPath == null) {
                        try {
                            val imageData = loadResizedImagePixels(Resources.defailtWallpaper, 128, 128)
                            val colors = extractDominantColors(imageData.pixels, imageData.width, imageData.height, 5)
                            val (bgHex, primaryHex, outlineHex) = assignThemeColors(colors)
                            val updatedSettings = settings.copy(
                                secondaryColor = bgHex,
                                primaryColor = primaryHex,
                                accentColor = outlineHex,
                                cardSemanticSettings = settings.cardSemanticSettings?.copy(
                                    backgroundColor = bgHex,
                                    outlineColor = primaryHex,
                                ),
                                importantSemanticSettings = settings.importantSemanticSettings?.copy(
                                    backgroundColor = primaryHex,

                                ),
                                selectedSemanticSettings = settings.selectedSemanticSettings?.copy(
                                    backgroundColor = primaryHex,
                                    outlineColor = primaryHex
                                )
                            )
                            persistedThemeSettings.value = updatedSettings
                            appTheme.value = createTheme(preset, updatedSettings)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    val defaultBlur = 6f
                    val cachedFileName = "${defaultBlur}-default-wallpaper"
                    getBlurredCachedImage(cachedFileName) ?: blurAndCacheImage(
                        cachedFileName,
                        "default-wallpaper",
                        Resources.defailtWallpaper,
                        defaultBlur,
                        appTheme().background,
                        0.75f
                    )
                } else {
                    null
                }
            }


            image {
                scaleType = ImageScaleType.Crop
                ::source { wallpaper() }
                rView::shown {
                    wallpaper() != null
                }
            }
            blurredImage(PlaybackState.currentBook, remember {
                PlaybackState.currentBook() != null && persistedThemeSettings().showPlayingBookCoverAsWallpaper
            })
            blurredImage(bookToShowBlurredBackgroundCoverOf, remember {
                mainPageNavigator.currentPage() is BookDetailPage && persistedThemeSettings().showPlayingBookCoverOnNowPlayingAndBookDetail
            })

            col {
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
                            dynamicTheme {
                                val destination = mainPageNavigator.routes.render(navLink.destination.invoke())?.urlLikePath?.segments?.toList() ?: emptyList()
                                val currentPath = mainPageNavigator.currentPage()?.let { mainPageNavigator.routes.render(it) }?.urlLikePath?.segments?.toList() ?: emptyList()

                                // Helper: Fixes the bug where `any { }` returns false for empty destinations like `[]`
                                fun isMatch(path: List<String>, dest: List<String>): Boolean {
                                    return if (dest.isEmpty()) path.isEmpty() else dest.any { path.contains(it) }
                                }

                                // 1. Does the current screen match this specific tab directly?
                                var matchingScreen = isMatch(currentPath, destination)

                                // 2. If not, verify if the current screen matches ANY tab
                                if (!matchingScreen) {
                                    val allTabDests = navItems.map {
                                        mainPageNavigator.routes.render(it.destination.invoke())?.urlLikePath?.segments?.toList() ?: emptyList()
                                    }

                                    val isAnyTabActive = allTabDests.any { isMatch(currentPath, it) }

                                    // 3. If NO tab matches the current path (we are on a detail screen), check history
                                    if (!isAnyTabActive) {
                                        val historyPaths = mainPageNavigator.stack().mapNotNull {
                                            mainPageNavigator.routes.render(it)?.urlLikePath?.segments?.toList()
                                        }

                                        // Find the most recent history item that corresponds to a valid tab
                                        val lastActiveTab = historyPaths.reversed().firstNotNullOfOrNull { histPath ->
                                            allTabDests.firstOrNull { dest -> isMatch(histPath, dest) }
                                        }

                                        // If the last valid tab from history matches THIS link's destination, highlight it!
                                        if (lastActiveTab == destination) {
                                            matchingScreen = true
                                        }
                                    }
                                }

                                if (matchingScreen) SelectedTab else UnSelectedTab
                            }
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


interface FullScreen