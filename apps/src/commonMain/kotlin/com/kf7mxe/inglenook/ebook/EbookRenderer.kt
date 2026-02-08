package com.kf7mxe.inglenook.ebook

import com.lightningkite.kiteui.views.ViewWriter

/**
 * Platform-specific ebook renderer.
 * Renders ePub/PDF files in an embedded view.
 */
expect fun ViewWriter.ebookReader(
    downloadUrl: String,
    authHeader: String
)
