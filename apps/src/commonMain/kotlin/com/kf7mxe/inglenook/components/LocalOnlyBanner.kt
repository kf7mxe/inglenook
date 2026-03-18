package com.kf7mxe.inglenook.components

import com.kf7mxe.inglenook.storage.BookshelfRepository
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.core.rememberSuspending

fun ViewWriter.localOnlyBanner() {
    val endpointAvailable = rememberSuspending { BookshelfRepository.bookshelfEndpointAvailable() }

    shownWhen { endpointAvailable.state().ready && !endpointAvailable() }.row {
        padding = 0.5.rem
        gap = 0.5.rem
        themeChoice += ThemeDerivation {
            it.copy(
                id = "local-only-banner",
                background = Color.fromHexString("#1976D2"),
                foreground = Color.white,
            ).withBack
        }

        centered.icon {
            source = Icon.info
            description = "Info"
        }
        expanding.centered.text("Bookshelves are only stored on this device. Install or upgrade the Inglenook plugin on your jellyfin instance to sync across devices.")
    }
}
