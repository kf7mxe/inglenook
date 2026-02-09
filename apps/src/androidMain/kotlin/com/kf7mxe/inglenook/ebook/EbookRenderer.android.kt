package com.kf7mxe.inglenook.ebook

import android.content.Intent
import com.lightningkite.kiteui.views.AndroidAppContext
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.text

/**
 * Android implementation of ebook reader.
 * Launches a dedicated ReaderActivity with Readium's EpubNavigatorFragment.
 */
actual fun ViewWriter.ebookReader(
    bookId: String,
    downloadUrl: String,
    authHeader: String
) {
    // Launch the ReaderActivity
    val context = AndroidAppContext.applicationCtx
    val intent = ReaderActivity.createIntent(
        context = context,
        bookId = bookId,
        downloadUrl = downloadUrl,
        authHeader = authHeader
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
