package com.kf7mxe.inglenook.screens

import com.kf7mxe.inglenook.FullScreen
import com.kf7mxe.inglenook.cache.fetchCoverImage
import com.kf7mxe.inglenook.jellyfin.jellyfinClient
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.dialogPageNavigator
import com.lightningkite.kiteui.navigation.mainPageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTopEnd
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.rememberSuspending

class BookCoverFullscreenPage(val bookId: String, val coverImageId: String?) : Page, FullScreen {
    override val title = Constant("Cover")

    override fun ViewWriter.render() {
        val cachedCover = rememberSuspending {
            jellyfinClient().fetchCoverImage(coverImageId, bookId)
        }

        expanding.frame {
            val zoom = expanding.zoomableImage {
                scaleType = ImageScaleType.Fit
            }
            zoom.rView.reactive {
                zoom.source = cachedCover()
            }
            atTopEnd.padded.button {
                centered.icon {
                    source = Icon.close
                    description = "Close"
                }
                onClick {
                    mainPageNavigator.goBack()
                }
            }
        }
    }
}
