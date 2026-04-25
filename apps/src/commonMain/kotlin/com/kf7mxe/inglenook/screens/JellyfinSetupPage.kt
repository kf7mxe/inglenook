package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.Resources
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.demo.DemoMode
import com.kf7mxe.inglenook.jellyfin.JellyfinClient
import com.kf7mxe.inglenook.jellyfin.addServer
import com.kf7mxe.inglenook.jellyfin.jellyfinServers
import com.kf7mxe.inglenook.visibility
import com.kf7mxe.inglenook.storage.DangerSemantic
import com.kf7mxe.inglenook.visibilityOff
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.kf7mxe.inglenook.FullScreen
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.jellyfin.hasSeenDiagnosticsPrompt
import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.kiteui.setClipboardText
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.reactive.context.invoke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

enum class LoginMethod { UsernamePassword, QuickConnect }

@Routable("/connect-jellyfin")
class JellyfinSetupPage : Page, FullScreen {
    override val title get() = Constant("Connect to Jellyfin")

    override fun ViewWriter.render() {
        // Step tracking: 1 = server URL, 2 = authentication
        val step = Signal(1)
        val serverUrl = Signal("")
        val serverName = Signal<String?>(null)
        val errorMessage = Signal<String?>(null)
        val loginMethod = Signal(LoginMethod.UsernamePassword)

        // Username/Password state
        val username = Signal("")
        val password = Signal("")
        val showPassword = Signal(false)

        // Quick Connect state
        val quickConnectCode = Signal<String?>(null)
        val quickConnectSecret = Signal<String?>(null)
        val isPolling = Signal(false)
        var pollingJob: Job? = null

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
                    centered.h1 { content = "Inglenook" }
                    centered.subtext {
                        ::content {
                            if (step() == 1) "Connect to your Jellyfin server"
                            else "Sign in to ${serverName() ?: serverUrl()}"
                        }
                    }
                    sizeConstraints(width = 7.rem).image {
                        source = Resources.jellyfinIcon
                    }
                }

                // Cancel button (if user already has servers)
                shownWhen { jellyfinServers.value.isNotEmpty() }.button {
                    centered.text("Cancel")
                    onClick {
                        mainPageNavigator.goBack()
                    }
                }

                // ===== STEP 1: Server URL =====
                shownWhen { step() == 1 }.card.col {
                    gap = 1.rem

                    col {
                        gap = 0.25.rem
                        text("Server URL")
                        fieldTheme.textInput {
                            hint = "https://your-server.com"
                            keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                            content bind serverUrl
                        }
                    }

                    button {
                        centered.text("Connect")
                        action = Action("Connect") {
                            errorMessage.value = null
                            val url = serverUrl.value.trim().trimEnd('/')
                            if (url.isBlank()) {
                                errorMessage.value = "Please enter a server URL"
                                return@Action
                            }

                            // Demo mode trigger for app store testers
                            if (url.equals("appstoretester@inglenook.com", ignoreCase = true)) {
                                DemoMode.activate()
                                mainPageNavigator.navigate(HomePage())
                                return@Action
                            }

                            serverUrl.value = url

                            val client = JellyfinClient(url)
                            val info = client.getServerInfo()
                            if (info != null) {
                                serverName.value = info.ServerName
                                step.value = 2
                            } else {
                                errorMessage.value = "Could not connect to server. Check the URL and try again."
                            }
                        }
                        themeChoice += ImportantSemantic
                    }
                }

                // ===== STEP 2: Authentication =====
                shownWhen { step() == 2 }.card.col {
                    gap = 1.rem

                    // Back button
                    button {
                        row {
                            gap = 0.25.rem
                            icon(Icon.arrowBack, "Back")
                            text("Change Server")
                        }
                        onClick {
                            stopPolling()
                            quickConnectCode.value = null
                            quickConnectSecret.value = null
                            errorMessage.value = null
                            step.value = 1
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
                                if (loginMethod() == LoginMethod.UsernamePassword) {
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
                                if (loginMethod() == LoginMethod.QuickConnect) {
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
                            fieldTheme.textInput {
                                hint = "Username"
                                keyboardHints = KeyboardHints(KeyboardCase.None, KeyboardType.Text)
                                content bind username
                            }
                        }

                        col {
                            gap = 0.25.rem
                            text("Password")
                            row {
                                gap = 0.5.rem
                                // Hidden password input
                                expanding.fieldTheme.textInput {
                                    hint = "Password"
                                    ::keyboardHints{
                                        if(showPassword()) KeyboardHints(KeyboardCase.None, KeyboardType.Text) else KeyboardHints.password
                                    }
                                    keyboardHints = KeyboardHints.password
                                    content bind password
                                }
                                button {
                                    icon {
                                        ::source { if (showPassword()) Icon.visibility else Icon.visibilityOff }
                                        ::description { if (showPassword()) "Hide password" else "Show password" }
                                    }
                                    onClick { showPassword.value = !showPassword.value }
                                }
                            }
                        }

                        button {
                            centered.text("Sign In")
                            action = Action("Sign In") {
                                errorMessage.value = null
                                val client = JellyfinClient(serverUrl.value)
                                val config = client.authenticate(username.value, password.value)
                                addServer(config)
                                if (!hasSeenDiagnosticsPrompt.value) {
                                    mainPageNavigator.navigate(DiagnosticsOnboardingPage())
                                } else {
                                    mainPageNavigator.navigate(LibrarySelectionPage())
                                }
                            }
                            themeChoice += ImportantSemantic
                        }
                    }

                    // Quick Connect form
                    shownWhen { loginMethod() == LoginMethod.QuickConnect }.col {
                        gap = 1.rem

                        subtext {
                            content = "Quick Connect lets you sign in by authorizing from another device that's already logged into your Jellyfin server."
                        }

                        // Show code when available
                        shownWhen { quickConnectCode() != null && isPolling() }.centered.col {
                            gap = 1.rem
                            padding = 1.rem

                            text { content = "Enter this code on your Jellyfin server:" }
                            card.row {
                                expanding.h1 {
                                    ::content { quickConnectCode() ?: "" }
                                    themeChoice += ThemeDerivation {
                                        it.copy("qc-code", font = it.font.copy(size = 2.5.rem)).withoutBack
                                    }
                                }
                                button{
                                    icon(Icon.copy,"copy")
                                    onClick {
                                        quickConnectCode()?.let{code->
                                            context.setClipboardText(code)
                                            toast("Copied to clipboard")
                                        }?: throw Exception("Clipboard text could not be copied.")
                                    }
                                }
                            }

                            row {
                                gap = 0.5.rem
                                inglenookActivityIndicator()
                                subtext { content = "Waiting for authorization..." }
                            }

                            button {
                                text("Cancel")
                                onClick {
                                    stopPolling()
                                    quickConnectCode.value = null
                                    quickConnectSecret.value = null
                                    loginMethod.set(LoginMethod.UsernamePassword)
                                }
                            }
                        }

                        // Get Code button
                        shownWhen { quickConnectCode() == null || !isPolling() }.
                        button {
                            centered.text("Get Code")
                            action = Action("Get Code") {
                                errorMessage.value = null
                                val client = JellyfinClient(serverUrl.value)

                                val enabled = client.isQuickConnectEnabled()
                                if (!enabled) {
                                    errorMessage.value = "Quick Connect is not enabled on this server"
                                    return@Action
                                }

                                val result = client.initiateQuickConnect()
                                if (result.Code == null || result.Secret == null) {
                                    errorMessage.value = "Failed to initiate Quick Connect"
                                    return@Action
                                }

                                quickConnectCode.value = result.Code
                                quickConnectSecret.value = result.Secret
                                isPolling.value = true

                                pollingJob = AppScope.launch {
                                    println("DEBUG pollingJob")
                                    var attempts = 0
                                    val maxAttempts = 60

                                    while (isPolling.value && attempts < maxAttempts) {
                                        println("DEBUG in polling loop ${isPolling()} attempt ${attempts / maxAttempts} ")
                                        delay(5000)
                                        attempts++

                                        try {
                                            val secret = quickConnectSecret.value ?: break
                                            val authorized = client.checkQuickConnectStatus(secret)

                                            if (authorized) {
                                                val config = client.authenticateWithQuickConnect(secret)
                                                addServer(config)
                                                stopPolling()
                                                if (!hasSeenDiagnosticsPrompt.value) {
                                                    mainPageNavigator.navigate(DiagnosticsOnboardingPage())
                                                } else {
                                                    mainPageNavigator.navigate(LibrarySelectionPage())
                                                }
                                                return@launch
                                            }
                                        } catch (e: Exception) {
                                            println("DEBUG e Exception: ${e.message}")
                                            // Continue polling on error
                                        }
                                    }

                                    if (isPolling.value) {
                                        errorMessage.value = "Quick Connect request timed out"
                                        stopPolling()
                                        quickConnectCode.value = null
                                        quickConnectSecret.value = null
                                    }
                                }
                            }
                            themeChoice += ImportantSemantic
                        }
                    }
                }

                // Error message (shared between both steps)
                shownWhen { errorMessage() != null }.text {
                    ::content { errorMessage() ?: "" }
                    themeChoice += DangerSemantic
                }
            }
        }
    }
}
