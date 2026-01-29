package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.jellyfin.JellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServerConfig
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Routable("/connect-jellyfin")
class JellyfinSetupPage : Page {
    override val title get() = Constant("Connect to Jellyfin")

    override fun ViewWriter.render() {
        val serverUrl = Signal("")
        val username = Signal("")
        val password = Signal("")
        val isLoading = Signal(false)
        val errorMessage = Signal<String?>(null)

        centered.col {
            padding = 2.rem
            gap = 1.rem

            sizedBox(SizeConstraints(maxWidth = 24.rem)).col {
                gap = 1.5.rem

                centered.col {
                    gap = 0.5.rem
                    h1 { content = "Inglenook" }
                    subtext { content = "Connect to your Jellyfin server" }
                }

                card.col {
                    gap = 1.rem

                    col {
                        gap = 0.25.rem
                        text("Server URL")
                        textField {
                            hint = "https://your-server.com"
                            keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                            content bind serverUrl
                        }
                    }

                    col {
                        gap = 0.25.rem
                        text("Username")
                        textField {
                            hint = "Username"
                            keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                            content bind username
                        }
                    }

                    col {
                        gap = 0.25.rem
                        text("Password")
                        textField {
                            hint = "Password"
                            keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                            content bind password
                            // Note: KiteUI doesn't have a password field type,
                            // but the server communication is secure over HTTPS
                        }
                    }

                    shownWhen { errorMessage() != null }.text {
                        ::content { errorMessage() ?: "" }
                        themeChoice += ThemeDerivation { it.copy("test",foreground = Color.red).withoutBack }
                    }

                    button {
                        shownWhen { !isLoading() }.text("Connect")
                        shownWhen { isLoading() }.activityIndicator()
                        onClick {
                            AppScope.launch {
                                isLoading.value = true
                                errorMessage.value = null
                                try {
                                    val client = JellyfinClient(serverUrl.value)
                                    val config = client.authenticate(username.value, password.value)
                                    if (config != null) {
                                        jellyfinServerConfig.value = config
                                        jellyfinClient.value = client
                                        mainPageNavigator.navigate(DashboardPage())
                                    } else {
                                        errorMessage.value = "Authentication failed"
                                    }
                                } catch (e: Exception) {
                                    errorMessage.value = e.message ?: "Connection failed"
                                } finally {
                                    isLoading.value = false
                                }
                            }
                        }
                        themeChoice += ImportantSemantic
                    }
                }
            }
        }
    }
}
