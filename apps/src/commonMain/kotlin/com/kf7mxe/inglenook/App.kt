package com.kf7mxe.inglenook

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.navigation.dialogPageNavigator
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atStart
import com.lightningkite.kiteui.views.bar
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.compact
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.appBase
import com.lightningkite.kiteui.views.l2.applySafeInsets
import com.lightningkite.kiteui.views.l2.coordinatorFrame
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.views.l2.navigatorView
import com.kf7mxe.inglenook.components.PlaybackControls
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.playback.PlaybackState
import com.kf7mxe.inglenook.screens.*
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.onRemove
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import kotlinx.coroutines.launch

// Default theme - Cozy forest green
val defaultTheme = createTheme(ThemePreset.Cozy)
val appTheme = Signal<Theme>(defaultTheme)

// Current theme preset setting
val currentThemePreset = Signal<ThemePreset>(ThemePreset.Cozy)

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

fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator) {
    val mainNavPages = listOf(
        NavLink("Home", Icon.home) { DashboardPage() },
        NavLink("Books", Icon.book) { BooksPage() },
        NavLink("Authors", Icon.person) { AuthorsPage() },
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
        frame {
            OuterSemantic.onNext.col {
                beforeNextElementSetup {
                    applySafeInsets(bottom = false)
                }

                // Top bar with back button and title
                bar.frame {
                    atStart.button {
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

                    centered.h3 {
                        ::content {
                            mainPageNavigator.currentPage()?.title?.invoke() ?: ""
                        }
                    }
                }

                // Main content area with coordinator frame for bottom sheet
                expanding.coordinatorFrame {
                    coordinatorFrame = this
                    expanding.navigatorView(navigator)

                    // Now playing bottom sheet - only show when something is playing
                    // and not on setup page
//                    shownWhen {
//                        val currentPage = mainPageNavigator.currentPage()
//                        val hasBook = PlaybackState.currentBook() != null
//                        hasBook && currentPage !is JellyfinSetupPage
//                    }.
                    nowPlaying(BottomSheetState.COLLAPSED)
                }

                // Bottom navigation bar
                beforeNextElementSetup {
                    applySafeInsets(top = false)
                }.shownWhen {
                    val currentPage = mainPageNavigator.currentPage()
                    currentPage !is JellyfinSetupPage && !AppState.softInputOpen()
                }.bottomBar(mainNavPages)
            }
        }
    }
}

fun ViewWriter.bottomBar(navItems: List<NavLink>) {
    unpadded.row {
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

fun ViewWriter.nowPlaying(startState: BottomSheetState) {
    coordinatorFrame?.bottomSheet(
        peekSize = 1.rem,
        partialRatio = 0.2f,
        startState = BottomSheetState.COLLAPSED
    ) {
        with(this@bottomSheet.split()) {
            card.col {
                applySafeInsets(top = false, bottom = true)
                gap = 0.0.rem
                padding = 0.rem

                // Collapsed state header with drag handle
                centered.button {
                    gap = 0.rem
                    padding = 0.5.rem
                    col {
                        gap = 0.rem
                        centered.icon(Icon.dragHandle, "Drag")
                    }
                    onClick {
                        if (it.state() == BottomSheetState.COLLAPSED) {
                            it.state.set(BottomSheetState.PARTIALLY_EXPANDED)
                        } else {
                            it.state.set(BottomSheetState.COLLAPSED)
                        }
                    }
                }

                // Mini player row (collapsed view)
                shownWhen { it.state() == BottomSheetState.COLLAPSED }.row {
                    padding = 0.75.rem
                    gap = 0.75.rem

                    // Thumbnail
                    sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)).frame {
                            image {
                                rView::shown{
                                    PlaybackState.currentBook() != null
                                }
                                ::source {
                                    PlaybackState.currentBook()?.coverImageId?.let{id ->
                                        ImageRemote(id)
                                    }
                                    }
                                scaleType = ImageScaleType.Crop
                            }
                            centered.icon{
                                ::shown {
                                    PlaybackState.currentBook() == null
                                }
                                source =Icon.book}

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

                    // Play/Pause button
                    button {
                        centered.icon {
                            ::source { if (PlaybackState.isPlaying()) Icon.pause else Icon.playArrow }
                            ::description { if (PlaybackState.isPlaying()) "Pause" else "Play" }
                        }
                        onClick { PlaybackState.togglePlayPause() }
                    }
                }

                // Expanded view with full controls
                shownWhen { it.state() != BottomSheetState.COLLAPSED }.expanding.scrolls.col {
                    padding = 1.rem
                    gap = 1.5.rem

                    // Large cover image
                    centered.sizedBox(SizeConstraints(maxWidth = 16.rem, maxHeight = 16.rem)).frame {
                            image {
                                rView::shown{
                                    PlaybackState.currentBook() != null
                                }
                                ::source {                            PlaybackState.currentBook()?.coverImageId?.let{id ->
                                    ImageRemote(id)
                                } }
                                scaleType = ImageScaleType.Fit
                            }
                            centered.icon{
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
                    centered.text {
                        ::content {
                            PlaybackState.currentChapter()?.name ?: ""
                        }
                    }

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
                                    if(PlaybackState.playbackSpeed() == speed) ImportantSemantic else null
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
                    space(2.0)
                }

                onRemove {
                    // Re-add the bottom sheet when it's removed
                    nowPlaying(BottomSheetState.COLLAPSED)
                }
            }
        }
    }
}
