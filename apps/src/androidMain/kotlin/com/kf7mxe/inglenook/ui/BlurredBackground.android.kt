package com.kf7mxe.inglenook.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import com.lightningkite.reactive.core.Reactive

/**
 * Android implementation of blurred background.
 * Uses RenderEffect on Android 12+ for real blur, falls back to alpha overlay on older versions.
 */
actual fun ViewWriter.blurredImage(
    imageSource: Reactive<ImageSource?>,
    blurRadius: Float
) {
    image {
        this@image.rView::shown {
            imageSource() != null
        }
        ::source {
            imageSource()
        }
        scaleType = ImageScaleType.Crop

        // Apply blur effect on Android 12+
        val imageView = rView.native as? ImageView
        if (imageView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blurEffect = RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
                imageView.setRenderEffect(blurEffect)
            } catch (e: Exception) {
                // Fallback: just show the image with reduced alpha
                imageView.alpha = 0.3f
            }
        } else if (imageView != null) {
            // Fallback for older Android: show with reduced alpha as "frosted" effect
            imageView.alpha = 0.3f
        }
    }
}
