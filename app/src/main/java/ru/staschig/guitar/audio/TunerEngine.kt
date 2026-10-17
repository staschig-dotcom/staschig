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
 * - берём «сырой» звук (UNPROCESSED / VOICE_RECOGNITION): в обычном режиме MIC Android включает
 *   шумоподавление и автогромкость, которые глушат ровный звук струны как «шум»;
 * - порог громкости не фиксированный, а подстраивается под шум комнаты ([NoiseGate]).
 */
class TunerEngine(private val onPitch: (freq: Float?, level: Float) -> Unit) {
    private val sampleRate = 44100
    private val frame = 4096

    @Volatile
    private var running = false
    private var worker: Thread? = null

    @SuppressLint("MissingPermission") // разрешение запрашивается на экране тюнера
    private fun open(minBuf: Int): AudioRecord? {
        for (src in listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )) {
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
            val detector = PitchDetector(sampleRate, frame)
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
}

/**
 * Порог громкости по шуму комнаты: фон отслеживается по тихим кадрам (быстро вниз, медленно вверх),
 * звук считается сигналом, если он в ~3 раза громче фона. Нижняя граница — чтобы не ловить шипение.
 */
class NoiseGate(private val minThreshold: Float = 0.0015f, private val ratio: Float = 3f) {
    private var floor = 0.002f

    fun update(rms: Float): Float {
        floor = if (rms < floor) floor + (rms - floor) * 0.3f else floor + (rms - floor) * 0.01f
        return maxOf(minThreshold, floor * ratio)
    }
}
