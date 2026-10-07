package ru.staschig.guitar.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * Поток с микрофона: отдаёт блоки сэмплов вместе со временем (System.nanoTime) первого сэмпла.
 * Время берётся из AudioRecord.getTimestamp — это момент захвата звука, а не момент чтения буфера.
 */
class MicStream(
    val sampleRate: Int = 44100,
    private val block: Int = 1024,
    private val source: Int = MediaRecorder.AudioSource.UNPROCESSED,
    private val onBlock: (samples: FloatArray, firstSampleNanos: Long) -> Unit,
) {
    @Volatile private var running = false
    private var worker: Thread? = null

    /** false — микрофон недоступен. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = createRecord(source, minBuf) ?: createRecord(MediaRecorder.AudioSource.MIC, minBuf) ?: return false
        running = true
        worker = thread(name = "mic", isDaemon = true) {
            val pcm = ShortArray(block)
            val samples = FloatArray(block)
            val ts = AudioTimestamp()
            var framesRead = 0L
            record.startRecording()
            try {
                while (running) {
                    var read = 0
                    while (read < block && running) {
                        val n = record.read(pcm, read, block - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read < block) continue
                    val now = System.nanoTime()
                    val start = if (record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        ts.nanoTime + (framesRead - ts.framePosition) * 1_000_000_000L / sampleRate
                    } else {
                        now - block * 1_000_000_000L / sampleRate
                    }
                    for (i in 0 until block) samples[i] = pcm[i] / 32768f
                    onBlock(samples, start)
                    framesRead += block
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(src: Int, minBuf: Int): AudioRecord? = runCatching {
        val r = AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, block * 4))
        if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
    }.getOrNull()

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
    }
}
