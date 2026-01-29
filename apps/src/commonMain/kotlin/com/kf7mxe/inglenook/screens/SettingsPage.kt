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
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.jellyfin.selectedLibraryId
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import kotlinx.coroutines.launch


@Routable("/settings")
class SettingsPage : Page {
    override val title get() = Constant("Settings")

    override fun ViewWriter.render() {
        val libraries = Signal<List<JellyfinLibrary>>(emptyList())
        val isLoadingLibraries = Signal(false)

        // Load libraries
        fun loadLibraries() {
            isLoadingLibraries.value = true
            AppScope.launch {
                try {
                    val client = jellyfinClient.value
                    if (client != null) {
                        libraries.value = client.getLibraries()
                    }
                } finally {
                    isLoadingLibraries.value = false
                }
            }
        }

        // Initial load
        loadLibraries()

        scrolls.col {
            padding = 1.rem
            gap = 1.5.rem

            // Jellyfin Server section
            col {
                gap = 0.5.rem
                h3 { content = "Jellyfin Server" }

                card.col {
                    gap = 0.5.rem

                    row {
                        expanding.col {
                            gap = 0.rem
                            text { ::content { jellyfinServerConfig()?.serverName ?: "Connected Server" } }
                            subtext { ::content { jellyfinServerConfig()?.serverUrl ?: "" } }
                            subtext { ::content { "Logged in as ${jellyfinServerConfig()?.username ?: "Unknown"}" } }
                        }
                        button {
                            text("Change")
                            onClick {
                                mainPageNavigator.navigate(JellyfinSetupPage())
                            }
                        }
                    }

                    separator()

                    button {
                        row {
                            expanding.text("Disconnect")
                            icon(Icon.logout, "Logout")
                        }
                        onClick {
                            jellyfinServerConfig.value = null
                            selectedLibraryId.value = null
                            mainPageNavigator.navigate(JellyfinSetupPage())
                        }
                        themeChoice += ThemeDerivation { it.copy(id = "danger", foreground = Color.red).withBack }
                    }
                }
            }

            // Library Selection section
            col {
                gap = 0.5.rem
                h3 { content = "Library" }

                card.col {
                    gap = 0.rem

                    shownWhen { isLoadingLibraries() }.centered.row {
                        padding = 1.rem
                        activityIndicator()
                        text("Loading libraries...")
                    }

                    shownWhen { !isLoadingLibraries() }.col {
                        gap = 0.rem

                        // "All Libraries" option
                        button {
                            row {
                                padding = 0.5.rem
                                expanding.text("All Libraries")
                                shownWhen { selectedLibraryId() == null }.icon(Icon.check, "Selected")
                            }
                            onClick {
                                selectedLibraryId.value = null
                            }
                            if (selectedLibraryId.value == null) {
                                themeChoice += SelectedSemantic
                            }
                        }

                        forEach(libraries) { library ->
                            separator()
                            button {
                                row {
                                    padding = 0.5.rem
                                    expanding.text(library.name)
                                    shownWhen { selectedLibraryId() == library.id }.icon(Icon.check, "Selected")
                                }
                                onClick {
                                    selectedLibraryId.value = library.id
                                }
                                if (selectedLibraryId.value == library.id) {
                                    themeChoice += SelectedSemantic
                                }
                            }
                        }
                    }
                }
            }

            // Theme section
            col {
                gap = 0.5.rem
                h3 { content = "Theme" }

                card.col {
                    gap = 0.rem

                    for (preset in ThemePreset.entries) {
                        if (preset == ThemePreset.Custom) continue // Skip custom for now

                        button {
                            row {
                                padding = 0.5.rem

                                // Color preview
                                sizedBox(SizeConstraints(width = 2.rem, height = 2.rem)).frame {
                                    val presetTheme = createTheme(preset)
                                    themeChoice += ThemeDerivation { presetTheme.withBack }
                                }

                                expanding.text { content = preset.name }

                                shownWhen { currentThemePreset() == preset }.icon(Icon.check, "Selected")
                            }
                            onClick {
                                currentThemePreset.value = preset
                                appTheme.value = createTheme(preset)
                            }
                            if (currentThemePreset.value == preset) {
                                themeChoice += SelectedSemantic
                            }
                        }

                        if (preset != ThemePreset.entries.filter { it != ThemePreset.Custom }.last()) {
                            separator()
                        }
                    }
                }
            }

            // Downloads section
            col {
                gap = 0.5.rem
                h3 { content = "Downloads" }

                card.col {
                    gap = 0.5.rem

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

            // About section
            col {
                gap = 0.5.rem
                h3 { content = "About" }

                card.col {
                    gap = 0.5.rem

                    row {
                        expanding.text("Version")
                        text("1.0.0")
                    }

                    separator()

                    row {
                        expanding.text("Inglenook")
                        subtext("Jellyfin Audiobook Player")
                    }
                }
            }
        }
    }
}
