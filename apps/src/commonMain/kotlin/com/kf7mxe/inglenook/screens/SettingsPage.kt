@file:OptIn(ExperimentalUuidApi::class)

package com.kf7mxe.inglenook.screens

import kotlin.uuid.ExperimentalUuidApi
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
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.librarySelector
import com.kf7mxe.inglenook.demo.DemoMode
import com.kf7mxe.inglenook.storage.DangerSemantic
import com.kf7mxe.inglenook.connectivity.ConnectivityState
import com.kf7mxe.inglenook.theming.createTheme
import com.kf7mxe.inglenook.jellyfin.jellyfinServers
import com.kf7mxe.inglenook.jellyfin.activeServerId
import com.kf7mxe.inglenook.jellyfin.switchToServer
import com.kf7mxe.inglenook.jellyfin.removeServer
import com.kf7mxe.inglenook.jellyfin.selectedLibraryIds
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.reactive.AppState
import com.lightningkite.kiteui.utils.getAppVersion
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.remember


@Routable("/settings")
class SettingsPage : Page {
    override val title get() = Constant("Settings")

    override fun ViewWriter.render() {
        scrolling.col {
            // Jellyfin Servers section (or Demo Mode indicator)
            col {
                h3 { content = "Jellyfin Servers" }

                if (DemoMode.isActive.value) {
                    // Demo mode indicator
                    card.col {
                        row {
                            expanding.col {
                                text { content = "Demo Mode" }
                                subtext { content = "Browsing public domain audiobooks" }
                            }
                        }

                        separator()

                        button {
                            row {
                                icon(Icon.close, "Exit")
                                expanding.text("Exit Demo Mode")
                            }
                            onClick {
                                DemoMode.deactivate()
                                mainPageNavigator.navigate(JellyfinSetupPage())
                            }
                            themeChoice += DangerSemantic
                        }
                    }
                } else {
                    card.col {
                        forEach(remember { jellyfinServers.value }) { server ->
                            val isActive = activeServerId.value == server._id.toString()

                            button {
                                row {
                                    expanding.col {
                                        text { content = server.displayName }
                                        subtext { content = server.serverUrl }
                                        subtext { content = "Logged in as ${server.username}" }
                                    }
                                    if (isActive) {
                                        centered.icon(Icon.check, "Active")
                                    }
                                }
                                onClick {
                                    if (!isActive) {
                                        switchToServer(server._id.toString())
                                        mainPageNavigator.navigate(HomePage())
                                    }
                                }
                                if (isActive) {
                                    dynamicTheme { SelectedSemantic }
                                }
                            }

                            // Remove button for each server
                            row {
                                expanding.space(1.0)
                                button {
                                    row {
                                        icon(Icon.close, "Remove")
                                        text("Remove")
                                    }
                                    onClick {
                                        removeServer(server._id.toString())
                                        if (jellyfinServers.value.isEmpty()) {
                                            mainPageNavigator.navigate(JellyfinSetupPage())
                                        }
                                    }
                                    themeChoice += DangerSemantic
                                }
                            }

                            separator()
                        }

                        // Add server button
                        button {
                            row {
                                icon(Icon.add, "Add")
                                expanding.text("Add Server")
                            }
                            onClick {
                                mainPageNavigator.navigate(JellyfinSetupPage())
                            }
                            themeChoice += ImportantSemantic
                        }
                    }
                }
            }



            // Theme section
            col {
                h3 { content = "Theme" }
                text{
                    ::content {
                        "Current Theme: ${currentThemePreset().displayName}"
                    }
                }
                    // Link to full theme settings
                    card.button {
                        row {
                            centered.text { content = "Change Theme" }
                            icon(Icon.themePalette, "theme")
                            expanding.space()
                            icon(Icon.chevronRight,"View")
                        }
                        onClick{ mainPageNavigator.navigate(ThemeSettingsPage()) }
                    }
//                }
            }

            // Connectivity section
            col {
                h3 { content = "Connectivity" }

                card.col {

                    row {
                        expanding.col {
                            text("Offline Mode")
                            subtext {
                                ::content {
                                    if (ConnectivityState.offlineMode()) "Currently offline"
                                    else "Use only downloaded books and cached data"
                                }
                            }
                        }
                        button {
                            text {
                                ::content {
                                    if (ConnectivityState.offlineMode()) "Go Online" else "Go Offline"
                                }
                            }
                            onClick {
                                if (ConnectivityState.offlineMode.value) {
                                    ConnectivityState.exitOfflineMode()
                                } else {
                                    ConnectivityState.enterManualOfflineMode()
                                }
                            }
                        }
                    }

                    // Banner: server is reachable while in manual offline mode
                    shownWhen { ConnectivityState.offlineMode() && ConnectivityState.serverReachable() }.card.row {
                        expanding.col {
                            text("Server available")
                            subtext("Your Jellyfin server is reachable again.")
                        }
                        button {
                            text("Go Online")
                            onClick {
                                ConnectivityState.exitOfflineMode()
                            }
                            themeChoice += ImportantSemantic
                        }
                    }
                }
            }

            // Downloads section
            col {
                h3 { content = "Downloads" }

                card.col {
                    button {
                        row {
                            expanding.col {
                                text("Manage Downloads")
                                subtext("View and delete downloaded audiobooks")
                            }
                            icon(Icon.chevronRight, "Go")
                        }
                        onClick {
                            mainPageNavigator.navigate(DownloadsPage())
                        }
                    }
                }
            }


            // Playback section
            col {
                h3 { content = "Playback" }
                card.col {
                    row {
                        expanding.col {
                            text("Auto-resume on open")
                            subtext("Continue playing where you left off when the app opens")
                        }
                        switch {
                            checked bind autoResumeOnOpen
                        }
                    }
                }
            }

            // Diagnostics section
            col {
                h3 { content = "Diagnostics" }
                card.col {
                    row {
                        expanding.col {
                            text("Send crash reports")
                            subtext("Help improve Inglenook by sending anonymous crash reports")
                        }
                        switch {
                            checked bind diagnosticsEnabled
                        }
                    }
                }
            }

            // Library Selection section
            col {
                row {
                    expanding.h3 { content = "Libraries" }
                    subtext {
                        ::content {
                            val selected = selectedLibraryIds()
                            if (selected.isEmpty()) "All" else "${selected.size} selected"
                        }
                    }
                }

                librarySelector()
            }


            // About section
            col {
                h3 { content = "About" }
                card.col {
                    row {
                        expanding.text("Version")
                        text{
                            ::content {
                                getAppVersion()
                            }
                        }
                    }

                    separator()

                    row {
                        expanding.text("Inglenook")
                        subtext("Jellyfin Audiobook Player")
                    }


                }
            }
            card.button {
                row {
                    expanding.text("Acknowledgements")
                    icon(Icon.chevronRight, "Go")
                }
                onClick {
                    mainPageNavigator.navigate(AcknowledgementsPage())
                }
            }
            space()
        }
    }
}
