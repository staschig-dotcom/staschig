package ru.staschig.guitar.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/** Захват микрофона и детекция высоты тона в фоновом потоке. */
class TunerEngine(private val onPitch: (Float?) -> Unit) {
    private val sampleRate = 44100
    private val frame = 4096

    @Volatile
    private var running = false
    private var worker: Thread? = null

    @SuppressLint("MissingPermission") // разрешение запрашивается на экране тюнера
    fun start() {
        if (running) return
        running = true
        worker = thread(name = "tuner", isDaemon = true) {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, frame * 2) * 2,
                )
            } catch (e: Exception) {
                running = false
                return@thread
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                running = false
                return@thread
            }
            val detector = PitchDetector(sampleRate, frame)
            val pcm = ShortArray(frame)
            val samples = FloatArray(frame)
            val recent = ArrayDeque<Float>()
            record.startRecording()
            try {
                while (running) {
                    var read = 0
                    while (read < frame && running) {
                        val n = record.read(pcm, read, frame - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read < frame) continue
                    for (i in 0 until frame) samples[i] = pcm[i] / 32768f
                    val f = detector.detect(samples)
                    if (f == null || f < 30f || f > 1400f) {
                        recent.clear()
                        onPitch(null)
                    } else {
                        recent.addLast(f)
                        if (recent.size > 5) recent.removeFirst()
                        onPitch(recent.sorted()[recent.size / 2]) // медиана — гасит скачки октав
                    }
                }
            } finally {
                record.stop()
                record.release()
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(500)
        worker = null
    }
}
