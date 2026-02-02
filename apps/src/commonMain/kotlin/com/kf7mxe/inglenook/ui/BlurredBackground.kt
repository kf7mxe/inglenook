package com.kf7mxe.inglenook.ui

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.reactive.core.Reactive

/**
 * Composable function to render a blurred background image.
 * Platform-specific implementations handle the actual blur effect.
 */
expect fun ViewWriter.blurredImage(
    imageSource: Reactive<ImageSource?>,
    blurRadius: Float = 20f
)
