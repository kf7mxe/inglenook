package com.kf7mxe.inglenook.downloads

import com.kf7mxe.inglenook.AudioBook
import com.kf7mxe.inglenook.DownloadProgress
import com.kf7mxe.inglenook.DownloadedBook

actual object PlatformDownloader {
    actual suspend fun performDownload(
        book: AudioBook,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadedBook {
        throw NotImplementedError("iOS downloads not yet implemented")
    }

    actual suspend fun cancelDownload(bookId: String) {
        // Not implemented
    }

    actual suspend fun deleteFile(filePath: String) {
        // Not implemented
    }

    actual fun getDownloadsDirectory(): String {
        throw NotImplementedError("iOS downloads not yet implemented")
    }
}
