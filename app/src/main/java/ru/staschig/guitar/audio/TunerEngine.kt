package ru.staschig.guitar.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * Захват микрофона и детекция высоты тона в фоновом потоке.
 *
 * Чтобы тюнер слышал гитару на расстоянии:
 * - вход можно переключать ([SOURCES]): какой лучше — зависит от телефона;
 * - порог громкости очень низкий и подстраивается под шум комнаты ([NoiseGate]); от шума защищает
 *   сам YIN (он масштабонезависим и не находит период в шуме), а не порог по громкости.
 */
class TunerEngine(
    private val sourceIndex: Int = 0,
    private val onPitch: (freq: Float?, level: Float) -> Unit,
) {
    private val sampleRate = 44100
    private val frame = 4096

    @Volatile
    private var running = false
    private var worker: Thread? = null

    @SuppressLint("MissingPermission") // разрешение запрашивается на экране тюнера
    private fun open(minBuf: Int): AudioRecord? {
        val order = SOURCES.indices.sortedBy { if (it == sourceIndex) -1 else it }
        for (src in order.map { SOURCES[it] }) {
            val r = runCatching {
                AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, frame * 2) * 2)
            }.getOrNull() ?: continue
            if (r.state == AudioRecord.STATE_INITIALIZED) return r
            r.release()
        }
        return null
    }

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "tuner", isDaemon = true) {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = open(minBuf)
            if (record == null) {
                running = false
                return@thread
            }
            val detector = PitchDetector(sampleRate, frame, threshold = 0.2f)
            val gate = NoiseGate()
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
                    val rms = PitchDetector.rms(samples)
                    val threshold = gate.update(rms)
                    val f = detector.detect(samples, minRms = threshold)
                    if (f == null || f < 30f || f > 1400f) {
                        recent.clear()
                        onPitch(null, rms)
                    } else {
                        recent.addLast(f)
                        if (recent.size > 5) recent.removeFirst()
                        onPitch(recent.sorted()[recent.size / 2], rms) // медиана — гасит скачки октав
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

    companion object {
        /** Варианты входа: у разных телефонов лучше слышит разный. Сырой UNPROCESSED часто очень тихий. */
        val SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.UNPROCESSED,
        )
        val SOURCE_NAMES = listOf("Микрофон 1", "Микрофон 2", "Микрофон 3 (сырой)")
    }
}

/**
 * Порог громкости по шуму комнаты: фон отслеживается по тихим кадрам (быстро вниз, медленно вверх),
 * звук считается сигналом, если он в ~3 раза громче фона. Нижняя граница — чтобы не ловить шипение.
 */
class NoiseGate(private val minThreshold: Float = 0.0003f, private val ratio: Float = 2.5f) {
    private var floor = 0.0005f

    fun update(rms: Float): Float {
        floor = if (rms < floor) floor + (rms - floor) * 0.3f else floor + (rms - floor) * 0.01f
        return maxOf(minThreshold, floor * ratio)
    }
}
