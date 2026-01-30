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
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

enum class LoginMethod { UsernamePassword, QuickConnect }

@Routable("/connect-jellyfin")
class JellyfinSetupPage : Page {
    override val title get() = Constant("Connect to Jellyfin")

    override fun ViewWriter.render() {
        // Shared state
        val serverUrl = Signal("")
        val isLoading = Signal(false)
        val errorMessage = Signal<String?>(null)
        val loginMethod = Signal(LoginMethod.UsernamePassword)

        // Username/Password state
        val username = Signal("")
        val password = Signal("")

        // Quick Connect state
        val quickConnectCode = Signal<String?>(null)
        val quickConnectSecret = Signal<String?>(null)
        val isPolling = Signal(false)
        var pollingJob: Job? = null

        // Helper function to stop polling
        fun stopPolling() {
            pollingJob?.cancel()
            pollingJob = null
            isPolling.value = false
        }

        centered.col {
            padding = 2.rem
            gap = 1.rem

            sizedBox(SizeConstraints(maxWidth = 28.rem)).col {
                gap = 1.5.rem

                centered.col {
                    gap = 0.5.rem
                    h1 { content = "Inglenook" }
                    subtext { content = "Connect to your Jellyfin server" }
                }

                card.col {
                    gap = 1.rem

                    // Server URL (shared between both methods)
                    col {
                        gap = 0.25.rem
                        text("Server URL")
                        textInput {
                            hint = "https://your-server.com"
                            keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                            content bind serverUrl
                        }
                    }

                    separator()

                    // Tab buttons
                    row {
                        gap = 0.5.rem

                        expanding.button {
                            text("Username / Password")
                            onClick {
                                stopPolling()
                                quickConnectCode.value = null
                                quickConnectSecret.value = null
                                loginMethod.value = LoginMethod.UsernamePassword
                            }
                            dynamicTheme {
                                if (loginMethod.value == LoginMethod.UsernamePassword) {
                                    ImportantSemantic
                                } else null
                            }
                        }

                        expanding.button {
                            text("Quick Connect")
                            onClick {
                                loginMethod.value = LoginMethod.QuickConnect
                            }
                            dynamicTheme {
                                if (loginMethod.value == LoginMethod.QuickConnect) {
                                    ImportantSemantic
                                } else null
                            }
                        }
                    }

                    separator()

                    // Username/Password form
                    shownWhen { loginMethod() == LoginMethod.UsernamePassword }.col {
                        gap = 1.rem

                        col {
                            gap = 0.25.rem
                            text("Username")
                            textInput {
                                hint = "Username"
                                keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                                content bind username
                            }
                        }

                        col {
                            gap = 0.25.rem
                            text("Password")
                            textInput {
                                hint = "Password"
                                keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                                content bind password
                            }
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
                                        jellyfinServerConfig.value = config
                                        jellyfinClient.value = client
                                        mainPageNavigator.navigate(DashboardPage())
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

                    // Quick Connect form
                    shownWhen { loginMethod() == LoginMethod.QuickConnect }.col {
                        gap = 1.rem

                        // Instructions
                        subtext {
                            content = "Quick Connect lets you sign in by authorizing from another device that's already logged into your Jellyfin server."
                        }

                        // Show code when available
                        shownWhen { quickConnectCode() != null && isPolling() }.centered.col {
                            gap = 1.rem
                            padding = 1.rem

                            text { content = "Enter this code on your Jellyfin server:" }

                            // Large code display
                            h1 {
                                ::content { quickConnectCode() ?: "" }
                                themeChoice += ThemeDerivation {
                                    it.copy("qc-code", font = it.font.copy(size = 2.5.rem)).withBack
                                }
                            }

                            row {
                                gap = 0.5.rem
                                activityIndicator()
                                subtext { content = "Waiting for authorization..." }
                            }

                            button {
                                text("Cancel")
                                onClick {
                                    stopPolling()
                                    quickConnectCode.value = null
                                    quickConnectSecret.value = null
                                }
                            }
                        }

                        // Get Code button (shown when not polling)
                        shownWhen { quickConnectCode() == null || !isPolling() }.button {
                            shownWhen { !isLoading() }.text("Get Code")
                            shownWhen { isLoading() }.activityIndicator()
                            onClick {
                                AppScope.launch {
                                    isLoading.value = true
                                    errorMessage.value = null
                                    try {
                                        val client = JellyfinClient(serverUrl.value)

                                        // Check if Quick Connect is enabled
                                        val enabled = client.isQuickConnectEnabled()
                                        if (!enabled) {
                                            errorMessage.value = "Quick Connect is not enabled on this server"
                                            return@launch
                                        }

                                        // Initiate Quick Connect
                                        val result = client.initiateQuickConnect()
                                        if (result.Code == null || result.Secret == null) {
                                            errorMessage.value = "Failed to initiate Quick Connect"
                                            return@launch
                                        }

                                        quickConnectCode.value = result.Code
                                        quickConnectSecret.value = result.Secret
                                        isPolling.value = true

                                        // Start polling for authorization
                                        pollingJob = AppScope.launch {
                                            var attempts = 0
                                            val maxAttempts = 60 // 5 minutes at 5 second intervals

                                            while (isPolling.value && attempts < maxAttempts) {
                                                delay(5000) // Poll every 5 seconds
                                                attempts++

                                                try {
                                                    val secret = quickConnectSecret.value ?: break
                                                    val authorized = client.checkQuickConnectStatus(secret)

                                                    if (authorized) {
                                                        // Authenticate with the secret
                                                        val config = client.authenticateWithQuickConnect(secret)
                                                        jellyfinServerConfig.value = config
                                                        jellyfinClient.value = client
                                                        stopPolling()
                                                        mainPageNavigator.navigate(DashboardPage())
                                                        return@launch
                                                    }
                                                } catch (e: Exception) {
                                                    // Continue polling on error
                                                }
                                            }

                                            // Timeout
                                            if (isPolling.value) {
                                                errorMessage.value = "Quick Connect request timed out"
                                                stopPolling()
                                                quickConnectCode.value = null
                                                quickConnectSecret.value = null
                                            }
                                        }
                                    } catch (e: Exception) {
                                        errorMessage.value = e.message ?: "Failed to connect"
                                    } finally {
                                        isLoading.value = false
                                    }
                                }
                            }
                            themeChoice += ImportantSemantic
                        }
                    }

                    // Error message (shared)
                    shownWhen { errorMessage() != null }.text {
                        ::content { errorMessage() ?: "" }
                        themeChoice += ThemeDerivation { it.copy("error", foreground = Color.red).withoutBack }
                    }
                }
            }
        }
    }
}
