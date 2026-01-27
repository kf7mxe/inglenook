package com.kf7mxe.inglenook.playback

import com.kf7mxe.inglenook.AudioBook

actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()

class IosAudioPlayer : AudioPlayer {
    // TODO: Implement using AVPlayer with background audio session
    // This is a stub implementation

    private var currentBookId: String? = null
    private var currentPosition: Long = 0L
    private var isPlaying: Boolean = false

    override fun play(book: AudioBook, startPositionTicks: Long) {
        currentBookId = book.id
        currentPosition = startPositionTicks
        isPlaying = true

        // TODO: Initialize AVPlayer and start playback
        // Configure audio session for background playback
        // AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback)
        // AVAudioSession.sharedInstance().setActive(true)
    }

    override fun pause() {
        isPlaying = false
        // TODO: avPlayer.pause()
    }

    override fun resume() {
        isPlaying = true
        // TODO: avPlayer.play()
    }

    override fun stop() {
        isPlaying = false
        currentBookId = null
        currentPosition = 0L
        // TODO: avPlayer.pause(), release resources
    }

    override fun seek(positionTicks: Long) {
        currentPosition = positionTicks
        // TODO: avPlayer.seek(to: CMTime)
    }

    override fun setPlaybackSpeed(speed: Float) {
        // TODO: avPlayer.rate = speed
    }

    override fun getCurrentPosition(): Long {
        // TODO: Return actual position from AVPlayer
        return currentPosition
    }
}
