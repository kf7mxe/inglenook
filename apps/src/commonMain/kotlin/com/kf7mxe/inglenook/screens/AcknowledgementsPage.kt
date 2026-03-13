package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.Constant
import com.kf7mxe.inglenook.util.openUrl

private data class Library(
    val name: String,
    val description: String,
    val url: String,
    val license: String,
)

private val libraries = listOf(
    Library(
        name = "KiteUI",
        description = "Cross-platform declarative UI framework for Kotlin Multiplatform",
        url = "https://github.com/lightningkite/kiteui",
        license = "MIT",
    ),
    Library(
        name = "Readable",
        description = "Reactive state management library for Kotlin",
        url = "https://github.com/lightningkite/readable",
        license = "MIT",
    ),
    Library(
        name = "Lottie for KiteUI",
        description = "Lottie animation rendering for KiteUI",
        url = "https://github.com/lightningkite/kiteui",
        license = "MIT",
    ),
    Library(
        name = "Kotlin",
        description = "Programming language and standard library",
        url = "https://github.com/JetBrains/kotlin",
        license = "Apache 2.0",
    ),
    Library(
        name = "Kotlinx Serialization",
        description = "Multiplatform serialization framework for Kotlin",
        url = "https://github.com/Kotlin/kotlinx.serialization",
        license = "Apache 2.0",
    ),
    Library(
        name = "Ktor",
        description = "Asynchronous HTTP client and server framework for Kotlin",
        url = "https://github.com/ktorio/ktor",
        license = "Apache 2.0",
    ),
    Library(
        name = "Readium",
        description = "Toolkit for rendering EPUB ebooks on Android",
        url = "https://github.com/readium/kotlin-toolkit",
        license = "BSD-3-Clause",
    ),
    Library(
        name = "AndroidX Media3 / ExoPlayer",
        description = "Media playback library for audio and video on Android",
        url = "https://github.com/androidx/media",
        license = "Apache 2.0",
    ),
    Library(
        name = "AndroidX AppCompat",
        description = "Backward-compatible Android UI components",
        url = "https://developer.android.com/jetpack/androidx/releases/appcompat",
        license = "Apache 2.0",
    ),
    Library(
        name = "AndroidX Fragment",
        description = "Android fragment lifecycle and navigation support",
        url = "https://developer.android.com/jetpack/androidx/releases/fragment",
        license = "Apache 2.0",
    ),
    Library(
        name = "AndroidX ConstraintLayout",
        description = "Flexible layout manager for complex Android UIs",
        url = "https://developer.android.com/jetpack/androidx/releases/constraintlayout",
        license = "Apache 2.0",
    ),
    Library(
        name = "Material Components for Android",
        description = "Material Design UI components for Android",
        url = "https://github.com/material-components/material-components-android",
        license = "Apache 2.0",
    ),
    Library(
        name = "epub.js",
        description = "JavaScript library for rendering EPUB ebooks in the browser",
        url = "https://github.com/futurepress/epub.js",
        license = "BSD-2-Clause",
    ),
    Library(
        name = "JSZip",
        description = "JavaScript library for creating and reading ZIP files",
        url = "https://github.com/Stuk/jszip",
        license = "MIT",
    ),
    Library(
        name = "IndexedDB (Juul Labs)",
        description = "Kotlin wrapper for browser IndexedDB storage",
        url = "https://github.com/JuulLabs/indexeddb",
        license = "Apache 2.0",
    ),
    Library(
        name = "BlurHash",
        description = "Compact image placeholder encoding and decoding",
        url = "https://github.com/vanniktech/blurhash",
        license = "MIT",
    ),
    Library(
        name = "Sentry Kotlin Multiplatform",
        description = "Crash reporting and error tracking",
        url = "https://github.com/getsentry/sentry-kotlin-multiplatform",
        license = "MIT",
    ),
)

@Routable("/settings/acknowledgements")
class AcknowledgementsPage : Page {
    override val title get() = Constant("Acknowledgements")

    override fun ViewWriter.render() {
        scrolling.col {
            h3 { content = "Open Source Libraries" }
            subtext { content = "This app is built with the following open source libraries." }

            for ((index, lib) in libraries.withIndex()) {
                card.col {
                    text { content = lib.name }
                    subtext { content = lib.description }

                    row {
                        expanding.subtext { content = "License: ${lib.license}" }
                        button {
                            row {
                                icon(Icon.externalLink, "Open")
                                text("Source")
                            }
                            onClick {
                                openUrl(lib.url)
                            }
                        }
                    }
                }
            }

            space()
        }
    }
}
