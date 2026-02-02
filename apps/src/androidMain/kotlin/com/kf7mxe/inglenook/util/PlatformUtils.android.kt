package com.kf7mxe.inglenook.util

import android.content.Intent
import android.net.Uri
import com.lightningkite.kiteui.views.AndroidAppContext

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    AndroidAppContext.applicationCtx.startActivity(intent)
}
