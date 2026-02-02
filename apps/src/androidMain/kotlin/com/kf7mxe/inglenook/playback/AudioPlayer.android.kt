package com.kf7mxe.inglenook.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.kf7mxe.inglenook.AudioBook
import com.lightningkite.kiteui.views.AndroidAppContext

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

class AndroidAudioPlayer : AudioPlayer {
    private var currentBookId: String? = null
    private var currentBook: AudioBook? = null
    private var pendingStartPosition: Long = 0L
    private var pendingLocalFilePath: String? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private fun getContext(): Context = AndroidAppContext.applicationCtx

    private fun ensureServiceStarted() {
        val context = getContext()
        val intent = Intent(context, PlaybackService::class.java)

        // Start the service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun connectToService(onConnected: () -> Unit) {
        if (mediaController != null) {
            onConnected()
            return
        }

        val context = getContext()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))

        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                mediaController = mediaControllerFuture?.get()
                onConnected()
            } catch (e: Exception) {
                // Connection failed, fall back to service instance
                onConnected()
            }
        }, MoreExecutors.directExecutor())
    }

    override fun play(book: AudioBook, startPositionTicks: Long, localFilePath: String?) {
        currentBookId = book.id
        currentBook = book
        pendingStartPosition = startPositionTicks
        pendingLocalFilePath = localFilePath

        // Start and connect to service
        ensureServiceStarted()

        // Small delay to let service start, then connect
        connectToService {
            // Use the service directly, passing local file path for offline playback
            PlaybackService.getInstance()?.playBook(book, startPositionTicks, localFilePath)
        }
    }

    override fun pause() {
        mediaController?.pause()
            ?: PlaybackService.getInstance()?.pause()
    }

    override fun resume() {
        mediaController?.play()
            ?: PlaybackService.getInstance()?.resume()
    }

    override fun stop() {
        mediaController?.stop()
            ?: PlaybackService.getInstance()?.stop()
        currentBookId = null
        currentBook = null
    }

    override fun seek(positionTicks: Long) {
        // Convert ticks to ms
        val positionMs = positionTicks / 10_000
        mediaController?.seekTo(positionMs)
            ?: PlaybackService.getInstance()?.seek(positionTicks)
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
            ?: PlaybackService.getInstance()?.setPlaybackSpeed(speed)
    }

    override fun getCurrentPosition(): Long {
        // Convert ms to ticks
        return mediaController?.currentPosition?.times(10_000)
            ?: PlaybackService.getInstance()?.getCurrentPositionTicks()
            ?: 0L
    }

    fun release() {
        mediaControllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        mediaController = null
        mediaControllerFuture = null
        currentBookId = null
        currentBook = null
    }
}
