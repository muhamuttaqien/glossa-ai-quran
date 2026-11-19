package com.muttaqien.quranreader

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Playback remains in the activity for this first version (no background service). */
class VersePlayer {
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var media: MediaPlayer? = null
    private var generation = 0

    fun play(url: String, onFinished: () -> Unit) {
        stop()
        error = null
        loading = true
        val current = generation
        try {
            val player = MediaPlayer()
            media = player
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            player.setOnPreparedListener {
                if (current == generation) {
                    loading = false
                    it.start()
                    playing = true
                }
            }
            player.setOnCompletionListener {
                if (current == generation) {
                    playing = false
                    onFinished()
                }
            }
            player.setOnErrorListener { _, _, _ ->
                if (current == generation) {
                    loading = false
                    playing = false
                    error = "Couldn't play audio. Check your connection and try again."
                }
                true
            }
            player.setDataSource(url)
            player.prepareAsync()
        } catch (_: Exception) {
            stop()
            error = "Couldn't play audio. Check your connection and try again."
        }
    }

    fun toggle() {
        val player = media ?: return
        if (loading) return
        try {
            if (player.isPlaying) player.pause() else player.start()
            playing = player.isPlaying
        } catch (_: IllegalStateException) { stop() }
    }

    fun stop() {
        generation++
        media?.release()
        media = null
        playing = false
        loading = false
    }
}
