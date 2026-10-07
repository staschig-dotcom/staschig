package ru.staschig.guitar.audio

import kotlin.math.sqrt

/**
 * Определение основной частоты алгоритмом YIN (de Cheveigné & Kawahara, 2002).
 * Чистый Kotlin без зависимостей от Android — покрыт юнит-тестами.
 */
class PitchDetector(
    private val sampleRate: Int,
    bufferSize: Int,
    private val threshold: Float = 0.15f,
) {
    private val half = bufferSize / 2
    private val yin = FloatArray(half)

    /** Частота в Гц или null, если сигнал слишком тихий/неустойчивый. */
    fun detect(buffer: FloatArray, minRms: Float = 0.01f): Float? {
        if (rms(buffer) < minRms) return null

        // 1. Разностная функция
        for (tau in 0 until half) {
            var sum = 0f
            for (i in 0 until half) {
                val d = buffer[i] - buffer[i + tau]
                sum += d * d
            }
            yin[tau] = sum
        }
        // 2. Кумулятивная нормализация
        yin[0] = 1f
        var running = 0f
        for (tau in 1 until half) {
            running += yin[tau]
            yin[tau] = if (running == 0f) 1f else yin[tau] * tau / running
        }
        // 3. Абсолютный порог
        var tauEstimate = -1
        var tau = 2
        while (tau < half) {
            if (yin[tau] < threshold) {
                while (tau + 1 < half && yin[tau + 1] < yin[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate == -1) return null

        // 4. Параболическая интерполяция
        val betterTau = if (tauEstimate in 1 until half - 1) {
            val s0 = yin[tauEstimate - 1]
            val s1 = yin[tauEstimate]
            val s2 = yin[tauEstimate + 1]
            val denom = 2f * (2f * s1 - s2 - s0)
            if (denom != 0f) tauEstimate + (s2 - s0) / denom else tauEstimate.toFloat()
        } else {
            tauEstimate.toFloat()
        }
        return sampleRate / betterTau
    }

    companion object {
        fun rms(buffer: FloatArray): Float {
            var sum = 0.0
            for (v in buffer) sum += v * v
            return sqrt(sum / buffer.size).toFloat()
        }
    }
}
