@file:OptIn(ExperimentalUuidApi::class)

package com.kf7mxe.inglenook.screens

import kotlin.uuid.ExperimentalUuidApi
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.forEach
import com.lightningkite.kiteui.views.l2.icon
import com.kf7mxe.inglenook.*
import com.kf7mxe.inglenook.components.inglenookActivityIndicator
import com.kf7mxe.inglenook.jellyfin.JellyfinClient
import com.kf7mxe.inglenook.jellyfin.jellyfinServers
import com.kf7mxe.inglenook.jellyfin.removeServer
import com.kf7mxe.inglenook.jellyfin.switchToServer
import com.kf7mxe.inglenook.jellyfin.updateServerConfig
import com.kf7mxe.inglenook.storage.DangerSemantic
import com.kf7mxe.inglenook.visibility
import com.kf7mxe.inglenook.visibilityOff
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Constant
import com.kf7mxe.inglenook.FullScreen
import com.lightningkite.kiteui.views.l2.toast
import com.lightningkite.kiteui.setClipboardText
import com.lightningkite.reactive.context.invoke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

enum class LoginPageMethod { UsernamePassword, QuickConnect }

@Routable("/login/{serverId}")
class LoginPage(val serverId: String) : Page, FullScreen {
    override val title get() = Constant("Sign In")

    override fun ViewWriter.render() {
        val server = jellyfinServers.value.find { it._id.toString() == serverId }
            ?: run {
                // Server no longer exists — bail to setup.
                mainPageNavigator.reset(JellyfinSetupPage())
                return
            }

        val errorMessage = Signal<String?>(null)
        val loginMethod = Signal(LoginPageMethod.UsernamePassword)

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

        fun onAuthenticated(config: JellyfinServerConfig) {
            // Keep the stable server ID so scoped data (libraries, downloads, progress) stays intact.
            updateServerConfig(config.copy(_id = server._id))
            mainPageNavigator.reset(HomePage())
        }

        val signInAction = Action("Sign In") {
            errorMessage.value = null
            try {
                val client = JellyfinClient(server.serverUrl)
                val config = client.authenticate(username.value, password.value)
                onAuthenticated(config)
            } catch (e: Exception) {
                if (e.message?.contains("401") == true) errorMessage.value = "Incorrect credentials."
                else errorMessage.value = e.message
            }
        }

        centered.scrolling.col {
            gap = 1.rem
            padding = 2.rem

            sizedBox(SizeConstraints(maxWidth = 28.rem)).col {
                gap = 1.5.rem

                centered.col {
                    gap = 0.5.rem
                    centered.h1 { content = "Signed out of" }
                    centered.h2 {
                        ::content { server.displayName }
                    }
                    server.username?.let { user ->
                        centered.subtext { content = "Was logged in as $user" }
                    } ?: centered.subtext { content = server.serverUrl }
                }

                card.col {
                    gap = 1.rem

                    // Tab buttons
                    row {
                        gap = 0.5.rem

                        expanding.button {
                            text("Username / Password")
                            onClick {
                                stopPolling()
                                quickConnectCode.value = null
                                quickConnectSecret.value = null
                                loginMethod.value = LoginPageMethod.UsernamePassword
                            }
                            dynamicTheme {
                                if (loginMethod() == LoginPageMethod.UsernamePassword) ImportantSemantic else null
                            }
                        }

                        expanding.button {
                            text("Quick Connect")
                            onClick { loginMethod.value = LoginPageMethod.QuickConnect }
                            dynamicTheme {
                                if (loginMethod() == LoginPageMethod.QuickConnect) ImportantSemantic else null
                            }
                        }
                    }

                    separator()

                    // Username/Password form
                    shownWhen { loginMethod() == LoginPageMethod.UsernamePassword }.col {
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
                                expanding.fieldTheme.textInput {
                                    hint = "Password"
                                    keyboardHints = KeyboardHints.password
                                    content bind password
                                    action = signInAction
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
                            action = signInAction
                            themeChoice += ImportantSemantic
                        }
                    }

                    // Quick Connect form
                    shownWhen { loginMethod() == LoginPageMethod.QuickConnect }.col {
                        gap = 1.rem

                        subtext {
                            content = "Quick Connect lets you sign in by authorizing from another device that's already logged into your Jellyfin server."
                        }

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
                                button {
                                    icon(Icon.copy, "copy")
                                    onClick {
                                        quickConnectCode()?.let { code ->
                                            context.setClipboardText(code)
                                            toast("Copied to clipboard")
                                        } ?: throw Exception("Clipboard text could not be copied.")
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
                                    loginMethod.set(LoginPageMethod.UsernamePassword)
                                }
                            }
                        }

                        shownWhen { quickConnectCode() == null || !isPolling() }.button {
                            centered.text("Get Code")
                            action = Action("Get Code") {
                                errorMessage.value = null
                                val client = JellyfinClient(server.serverUrl)

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
                                    var attempts = 0
                                    val maxAttempts = 60

                                    while (isPolling.value && attempts < maxAttempts) {
                                        delay(5000)
                                        attempts++

                                        try {
                                            val secret = quickConnectSecret.value ?: break
                                            val authorized = client.checkQuickConnectStatus(secret)

                                            if (authorized) {
                                                val config = client.authenticateWithQuickConnect(secret)
                                                stopPolling()
                                                onAuthenticated(config)
                                                return@launch
                                            }
                                        } catch (e: Exception) {
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

                    // Error message
                    shownWhen { errorMessage() != null }.text {
                        ::content { errorMessage() ?: "" }
                        themeChoice += DangerSemantic
                    }
                }

                // Other servers
                val others = jellyfinServers.value.filter { it._id.toString() != serverId }
                centered.subtext {
                    shown  = others.isNotEmpty()
                    content = "Other Servers" }
                    col {
                        gap = 0.5.rem
                        shown = others.isNotEmpty()
                        forEach(jellyfinServers) { other ->
                            if (other._id.toString() == serverId) return@forEach
                            val isLoggedIn = other.accessToken != null
                            button {
                                row {
                                    expanding.col {
                                        text { content = other.displayName }
                                        subtext {
                                            ::content {
                                                if (isLoggedIn) "Logged in as ${other.username ?: "user"}"
                                                else "Not logged in"
                                            }
                                        }
                                    }
                                    centered.icon(
                                        if (isLoggedIn) Icon.check else Icon.login,
                                        if (isLoggedIn) "Switch" else "Log In"
                                    )
                                }
                                onClick {
                                    if (isLoggedIn) {
                                        switchToServer(other._id.toString())
                                        mainPageNavigator.reset(HomePage())
                                    } else {
                                        mainPageNavigator.reset(LoginPage(other._id.toString()))
                                    }
                                }
                            }
                        }
                    }

                // Add New Server
                button {
                    row {
                        icon(Icon.add, "Add")
                        expanding.text("Add New Server")
                    }
                    onClick { mainPageNavigator.navigate(JellyfinSetupPage()) }
                    themeChoice += ImportantSemantic
                }

                // Remove This Server
                button {
                    row {
                        icon(Icon.close, "Remove")
                        expanding.text("Remove This Server")
                    }
                    onClick {
                        stopPolling()
                        removeServer(server._id.toString())
                        val remaining = jellyfinServers.value
                        when {
                            remaining.isEmpty() -> mainPageNavigator.reset(JellyfinSetupPage())
                            else -> {
                                val firstLoggedIn = remaining.firstOrNull { it.accessToken != null }
                                if (firstLoggedIn != null) {
                                    switchToServer(firstLoggedIn._id.toString())
                                    mainPageNavigator.reset(HomePage())
                                } else {
                                    mainPageNavigator.reset(LoginPage(remaining.first()._id.toString()))
                                }
                            }
                        }
                    }
                    themeChoice += DangerSemantic
                }
            }
        }
    }
}
