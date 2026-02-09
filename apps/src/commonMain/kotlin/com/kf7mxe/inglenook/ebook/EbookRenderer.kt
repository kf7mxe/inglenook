package com.kf7mxe.inglenook.ebook

import com.lightningkite.kiteui.views.ViewWriter

/**
 * Platform-specific ebook renderer.
 * Renders ePub/PDF files using Readium toolkit.
 * Android: Launches a dedicated ReaderActivity with Readium's EpubNavigatorFragment.
 * Web/JS: Renders directly into the DOM using epub.js with full reader controls.
 */
expect fun ViewWriter.ebookReader(
    bookId: String,
    downloadUrl: String,
    authHeader: String
)
