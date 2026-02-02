package com.kf7mxe.inglenook.ui

import com.lightningkite.kiteui.models.ImageScaleType
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.image
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIImageView
import platform.UIKit.UIVisualEffectView

/**
 * iOS implementation of blurred background using UIVisualEffectView.
 */
actual fun ViewWriter.blurredImage(
    imageSource: Reactve<ImageSource?>,
    blurRadius: Float
) {
    image {
        source = imageSource
        scaleType = ImageScaleType.Crop

        // On iOS, we can use UIVisualEffectView for blur
        // The blurRadius maps to light/regular/prominent styles
        val blurStyle = when {
            blurRadius < 15f -> UIBlurEffectStyle.UIBlurEffectStyleLight
            blurRadius < 35f -> UIBlurEffectStyle.UIBlurEffectStyleRegular
            else -> UIBlurEffectStyle.UIBlurEffectStyleProminent
        }

        // Apply blur effect to the image view
        val imageView = rView.native as? UIImageView
        if (imageView != null) {
            val blurEffect = UIBlurEffect.effectWithStyle(blurStyle)
            val blurEffectView = UIVisualEffectView(effect = blurEffect)
            blurEffectView.setFrame(imageView.bounds)
            blurEffectView.setAutoresizingMask(
                platform.UIKit.UIViewAutoresizingFlexibleWidth or
                platform.UIKit.UIViewAutoresizingFlexibleHeight
            )
            imageView.addSubview(blurEffectView)
        }
    }
}
