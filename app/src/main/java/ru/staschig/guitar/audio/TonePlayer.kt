package ru.staschig.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Эталонный звук струны — чтобы настраиваться на слух. */
object TonePlayer {
    private const val SAMPLE_RATE = 44100
    private var track: AudioTrack? = null

    fun play(freq: Float, seconds: Float = 2f) {
        stop()
        val n = (SAMPLE_RATE * seconds).toInt()
        val data = FloatArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = minOf(1.0, t * 50) * exp(-t * 1.2)
            // основной тон + обертоны, звучит ближе к струне, чем чистый синус
            (env * 0.5 * (sin(2 * PI * freq * t) + 0.5 * sin(4 * PI * freq * t) + 0.25 * sin(6 * PI * freq * t))).toFloat()
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(n * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, n, AudioTrack.WRITE_BLOCKING)
        t.play()
        track = t
    }

    fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
