package ru.staschig.guitar.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlin.math.pow

/**
 * Проигрыватель минусовок (ExoPlayer): MP3, AAC/M4A, OGG/Opus, FLAC, WAV и др.
 * Скорость меняется без изменения высоты, высота (транспонирование) — без изменения скорости.
 */
object BackingPlayer {
    private var player: ExoPlayer? = null
    var currentFile: File? = null
        private set

    var speed: Float = 1f
        private set
    var semitones: Int = 0
        private set

    /** Повтор фрагмента A–B (мс), null — без повтора. */
    var loopA: Long? = null
    var loopB: Long? = null

    private fun get(context: Context): ExoPlayer =
        player ?: ExoPlayer.Builder(context.applicationContext).build().also { player = it }

    fun load(context: Context, file: File) {
        val p = get(context)
        if (currentFile == file) return
        currentFile = file
        loopA = null
        loopB = null
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.prepare()
        applyParams()
    }

    val isPlaying: Boolean get() = player?.isPlaying == true
    val position: Long get() = player?.currentPosition ?: 0L
    val duration: Long get() = player?.duration?.takeIf { it > 0 } ?: 0L
    val hasError: Boolean get() = player?.playerError != null

    fun playPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(loopA ?: 0L)
            p.play()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms.coerceIn(0, duration.coerceAtLeast(0)))
    }

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.25f, 2f)
        applyParams()
    }

    fun setSemitones(value: Int) {
        semitones = value.coerceIn(-12, 12)
        applyParams()
    }

    private fun applyParams() {
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        player?.playbackParameters = PlaybackParameters(speed, pitch)
    }

    /** Вызывать периодически (~20 раз в секунду): держит повтор A–B. */
    fun tick() {
        val a = loopA
        val b = loopB
        val p = player ?: return
        if (a != null && b != null && b > a && p.isPlaying && p.currentPosition >= b) p.seekTo(a)
    }

    fun release() {
        player?.release()
        player = null
        currentFile = null
    }
}
