package ru.staschig.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Проигрывание готового звука: эталон струны, аккорды, примеры «как должно звучать». */
object TonePlayer {
    private const val SAMPLE_RATE = 44100
    private const val CHUNK = 4096

    @Volatile private var playing = false
    private var worker: Thread? = null
    private var track: AudioTrack? = null

    val isPlaying: Boolean get() = playing

    fun play(freq: Float, seconds: Float = 2f) {
        val n = (SAMPLE_RATE * seconds).toInt()
        playSamples(FloatArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = minOf(1.0, t * 50) * exp(-t * 1.2)
            // основной тон + обертоны, звучит ближе к струне, чем чистый синус
            (env * 0.5 * (sin(2 * PI * freq * t) + 0.5 * sin(4 * PI * freq * t) + 0.25 * sin(6 * PI * freq * t))).toFloat()
        })
    }

    /** Проигрывает звук (моно, 44.1 кГц) потоком — подходит и для длинных примеров. */
    fun playSamples(data: FloatArray) {
        stop()
        if (data.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
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
            .setBufferSizeInBytes(maxOf(minBuf, CHUNK * 4 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        playing = true
        t.play()
        worker = thread(name = "tone", isDaemon = true) {
            var pos = 0
            while (playing && pos < data.size) {
                val n = minOf(CHUNK, data.size - pos)
                t.write(data, pos, n, AudioTrack.WRITE_BLOCKING)
                pos += n
            }
            // Дать доиграть буферу, затем освободить.
            if (playing) Thread.sleep(300)
            playing = false
        }
    }

    fun stop() {
        playing = false
        worker?.join(500)
        worker = null
        track?.let {
            runCatching { it.pause(); it.flush(); it.stop() }
            it.release()
        }
        track = null
    }
}
