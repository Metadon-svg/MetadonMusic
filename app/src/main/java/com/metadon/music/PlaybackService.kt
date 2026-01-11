package com.metadon.music

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        // Создаем плеер на уровне класса, чтобы он был доступен везде
        val newPlayer = ExoPlayer.Builder(this).build()
        player = newPlayer
        mediaSession = MediaSession.Builder(this, newPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // Правильное освобождение ресурсов
        mediaSession?.let {
            it.player.release()
            it.release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
