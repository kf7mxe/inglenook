package com.kf7mxe.inglenook

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.exceptions.ExceptionToMessages
import com.lightningkite.kiteui.models.*
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
import com.lightningkite.kiteui.views.l2.overlayFrame
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.screens.*
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember

// Default theme - Cozy forest green
val defaultTheme = createTheme(ThemePreset.Cozy)
val appTheme = Signal<Theme>(defaultTheme)

// Current theme preset setting
val currentThemePreset = Signal<ThemePreset>(ThemePreset.Cozy)

// Navigation link helper
data class NavLink(
    val title: ReactiveContext.() -> String,
    val icon: ReactiveContext.() -> Icon,
    val count: (ReactiveContext.() -> Int?)? = null,
    val hidden: (ReactiveContext.() -> Boolean)? = null,
    val destination: ReactiveContext.() -> () -> Screen
)

fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator) {
    ExceptionToMessages.root.installLsError()

    val mainNavPages = listOf(
        NavLink(
            title = { "Home" },
            icon = { Icon.home }
        ) { { DashboardPage() } },
        NavLink(
            title = { "Books" },
            icon = { Icon.book }
        ) { { BooksPage() } },
        NavLink(
            title = { "Authors" },
            icon = { Icon.person }
        ) { { AuthorsPage() } },
        NavLink(
            title = { "Settings" },
            icon = { Icon.settings }
        ) { { SettingsPage() } },
    )

    // Check if Jellyfin is configured, if not go to setup
    AppScope.reactiveSuspending {
        val config = jellyfinServerConfig()
        if (config == null) {
            navigator.navigate(JellyfinSetupPage())
        } else {
            navigator.navigate(DashboardPage())
        }
    }

    return appBase(navigator, dialog) {
        frame {
            overlayFrame = this

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
                        ::shown { !mainPageNavigator.currentPage()?.title?.invoke().isNullOrBlank() }
                        ::content {
                            mainPageNavigator.currentPage()?.title?.invoke() ?: ""
                        }
                    }
                }

                // Main content area with coordinator frame for bottom sheet
                expanding.coordinatorFrame {
                    coordinatorFrame = this
                    expanding.navigatorView(navigator)

                    // Now playing bottom sheet - only show when we have something playing
                    // and not on full page screens
                    shownWhen {
                        mainPageNavigator.currentPage() !is UseFullPage
                    }.nowPlaying(BottomSheetState.COLLAPSED)
                }

                // Bottom navigation bar
                val bottomBarShown = remember {
                    mainPageNavigator.currentPage() !is UseFullPage && !AppState.softInputOpen()
                }
                beforeNextElementSetup {
                    applySafeInsets(top = false)
                }.shownWhen {
                    bottomBarShown()
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
                shown = false
                ::shown { navLink.hidden?.invoke() != true }
                col {
                    gap = 0.0.rem
                    centered.row {
                        centered.icon {
                            ::source { navLink.icon().copy(width = 1.5.rem, height = 1.5.rem) }
                            ::description { navLink.title() }
                        }
                        navLink.count?.let { count ->
                            centered.compact.frame {
                                shown = false
                                ::shown { count() != null }
                                space(0.01)
                                centered.text {
                                    ::content { count()?.takeIf { it > 0 }?.toString() ?: "" }
                                }
                            }
                        }
                    }
                    centered.subtext { ::content { navLink.title(this) } }
                }
                ::to { navLink.destination() }
            }
        }
    }
}

fun ViewWriter.nowPlaying(startState: BottomSheetState) {
    coordinatorFrame?.bottomSheet(
        peekSize = 6.rem,
        startState = startState
    ) {
        with(this@bottomSheet.split()) {
            card.col {
                applySafeInsets(top = false, bottom = true)
                gap = 0.0.rem
                padding = 0.rem

                // Collapsed state - drag handle and mini player
                centered.button {
                    gap = 0.rem
                    padding = 0.0.rem
                    col {
                        gap = 0.rem
                        padding = 0.0.rem
                        centered.icon(Icon.dragHandle, "dragHandle")
                        centered.text("Now Playing")
                    }

                    onClick {
                        if (it.state() == BottomSheetState.COLLAPSED) {
                            it.state.set(BottomSheetState.PARTIALLY_EXPANDED)
                        } else {
                            it.state.set(BottomSheetState.COLLAPSED)
                        }
                    }
                }

                onRemove {
                    nowPlaying(BottomSheetState.COLLAPSED)
                }
            }
        }
    }
}

// Marker interface for screens that should hide bottom bar and now playing
interface UseFullPage

// Abstract Screen class for pages
abstract class Screen {
    open val title: ReactiveContext.() -> String? = { null }
    abstract fun ViewWriter.render()
}
