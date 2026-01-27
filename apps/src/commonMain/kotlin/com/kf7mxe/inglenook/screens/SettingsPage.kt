package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.jellyfin.selectedLibraryId
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class SettingsPage(val unit: Unit = Unit) : Page {
    override val title: ReactiveContext.() -> String = { "Settings" }

    override fun ViewWriter.render() {
        val libraries = Signal<List<JellyfinLibrary>>(emptyList())

        // Load libraries
        AppScope.launch {
            try {
                val client = jellyfinClient.value
                if (client != null) {
                    libraries.value = client.getLibraries()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }

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
                            text {
                                ::content { jellyfinServerConfig()?.serverName ?: "Connected Server" }
                            }
                            subtext {
                                ::content { jellyfinServerConfig()?.serverUrl ?: "" }
                            }
                            subtext {
                                ::content { "Logged in as ${jellyfinServerConfig()?.username ?: "Unknown"}" }
                            }
                        }
                        button {
                            text("Change")
                            onClick {
                                mainPageNavigator.navigate(JellyfinSetupPage(editing = true))
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
                            mainPageNavigator.navigate(JellyfinSetupPage())
                        }
                        themeChoice = ThemeDerivation { it.copy(foreground = Color.red).withBack }.onNext
                    }
                }
            }

            // Library Selection
            col {
                gap = 0.5.rem
                h3 { content = "Library" }

                card.col {
                    gap = 0.5.rem
                    text("Select which library to use for audiobooks")

                    reactiveSuspending {
                        clearChildren()
                        val currentLibrary = selectedLibraryId()

                        for (library in libraries()) {
                            button {
                                row {
                                    expanding.text(library.name)
                                    if (library.id == currentLibrary) {
                                        icon(Icon.check, "Selected")
                                    }
                                }
                                onClick {
                                    selectedLibraryId.value = library.id
                                }
                                if (library.id == currentLibrary) {
                                    themeChoice = SelectedSemantic.onNext
                                }
                            }
                        }

                        if (libraries().isEmpty()) {
                            text("No libraries found")
                        }
                    }
                }
            }

            // Theme section
            col {
                gap = 0.5.rem
                h3 { content = "Appearance" }

                card.col {
                    gap = 0.5.rem
                    text("Theme")

                    row {
                        gap = 0.5.rem

                        for (preset in ThemePreset.entries.filter { it != ThemePreset.Custom }) {
                            button {
                                val presetTheme = createTheme(preset)
                                col {
                                    gap = 0.25.rem
                                    sizedBox(SizeConstraints(width = 3.rem, height = 3.rem)) {
                                        frame {
                                            themeChoice = ThemeDerivation {
                                                it.copy(background = presetTheme.accent).withBack
                                            }.onNext
                                        }
                                    }
                                    centered.subtext(preset.name)
                                }
                                onClick {
                                    currentThemePreset.value = preset
                                    appTheme.value = presetTheme
                                }
                                if (currentThemePreset.value == preset) {
                                    themeChoice = SelectedSemantic.onNext
                                }
                            }
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

                    separator()

                    row {
                        expanding.col {
                            text("Download over WiFi only")
                            subtext("Prevent downloads on mobile data")
                        }
                        toggle {
                            checked = true // TODO: Bind to actual setting
                        }
                    }
                }
            }

            // Playback section
            col {
                gap = 0.5.rem
                h3 { content = "Playback" }

                card.col {
                    gap = 0.5.rem

                    row {
                        expanding.col {
                            text("Skip forward")
                            subtext("Time to skip when pressing forward button")
                        }
                        text("30s") // TODO: Make configurable
                    }

                    separator()

                    row {
                        expanding.col {
                            text("Skip backward")
                            subtext("Time to skip when pressing backward button")
                        }
                        text("15s") // TODO: Make configurable
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
