package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.applySafeInsets
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.JellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class JellyfinSetupPage(val editing: Boolean = false) : Page, UseFullPage {
    override val title: ReactiveContext.() -> String = { "Connect to Jellyfin" }

    override fun ViewWriter.render() {
        val serverUrl = Signal("")
        val username = Signal("")
        val password = Signal("")
        val isLoading = Signal(false)
        val errorMessage = Signal<String?>(null)

        col {
            applySafeInsets()
            padding = 1.5.rem
            gap = 1.rem

            // Logo/Header
            centered.col {
                gap = 0.5.rem
                h1 {
                    content = "Inglenook"
                }
                subtext {
                    content = "Jellyfin Audiobook Player"
                }
            }

            space(1.0)

            // Server URL field
            col {
                gap = 0.25.rem
                label {
                    content = "Server URL"
                }
                textField {
                    hint = "https://jellyfin.example.com"
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind serverUrl
                }
            }

            // Username field
            col {
                gap = 0.25.rem
                label {
                    content = "Username"
                }
                textField {
                    hint = "Username"
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    content bind username
                }
            }

            // Password field
            col {
                gap = 0.25.rem
                label {
                    content = "Password"
                }
                textField {
                    hint = "Password"
                    keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                    password = true
                    content bind password
                }
            }

            // Error message
            shownWhen { errorMessage() != null }.text {
                ::content { errorMessage() ?: "" }
                themeChoice = ThemeDerivation { it.copy(foreground = Color.red).withoutBack }.onNext
            }

            space(1.0)

            // Connect button
            expanding.button {
                ::enabled { !isLoading() && serverUrl().isNotBlank() && username().isNotBlank() }

                centered.row {
                    gap = 0.5.rem
                    shownWhen { isLoading() }.activityIndicator()
                    text {
                        ::content { if (isLoading()) "Connecting..." else "Connect" }
                    }
                }

                onClick {
                    AppScope.launch {
                        isLoading.value = true
                        errorMessage.value = null

                        try {
                            val client = JellyfinClient(serverUrl.value.trimEnd('/'))
                            val config = client.authenticate(username.value, password.value)
                            jellyfinServerConfig.value = config
                            mainPageNavigator.navigate(DashboardPage())
                        } catch (e: Exception) {
                            errorMessage.value = e.message ?: "Connection failed"
                        } finally {
                            isLoading.value = false
                        }
                    }
                }

                themeChoice = ImportantSemantic.onNext
            }

            // Cancel button (only when editing existing config)
            if (editing) {
                button {
                    centered.text("Cancel")
                    onClick {
                        mainPageNavigator.goBack()
                    }
                }
            }
        }
    }
}
