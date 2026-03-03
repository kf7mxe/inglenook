package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.FullScreen
import com.kf7mxe.inglenook.Resources
import com.kf7mxe.inglenook.jellyfin.diagnosticsEnabled
import com.kf7mxe.inglenook.jellyfin.hasSeenDiagnosticsPrompt
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.reactive.core.Constant

class DiagnosticsOnboardingPage : Page, FullScreen {
    override val title get() = Constant("Diagnostics")

    override fun ViewWriter.render() {
        centered.col {
            padding = 2.rem
            gap = 1.rem

            sizedBox(SizeConstraints(maxWidth = 28.rem)).col {
                gap = 1.5.rem

                centered.col {
                    gap = 0.5.rem
                    centered.h1 { content = "Inglenook" }
                    sizeConstraints(width = 7.rem).image {
                        source = Resources.jellyfinIcon
                    }
                }

                card.col {
                    gap = 1.rem

                    h3 { content = "Help Improve Inglenook" }

                    text {
                        content = "You can help us improve Inglenook by allowing anonymous crash reports to be sent when something goes wrong."
                    }

                    subtext {
                        content = "No personal data or listening history is collected. You can change this at any time in Settings."
                    }

                    separator()

                    row {
                        expanding.col {
                            text("Send crash reports")
                            subtext("Anonymous diagnostic data")
                        }
                        switch {
                            checked bind diagnosticsEnabled
                        }
                    }
                }

                button {
                    centered.text("Continue")
                    onClick {
                        hasSeenDiagnosticsPrompt.value = true
                        mainPageNavigator.navigate(LibrarySelectionPage())
                    }
                    themeChoice += ImportantSemantic
                }
            }
        }
    }
}
