package ru.staschig.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Детектор атак (ударов по струнам) в потоке сэмплов.
 *
 * Режим LOW слушает полосу < ~1 кГц (гитара), режим HIGH — > ~3 кГц (щелчок метронома
 * на 4 кГц, используется для калибровки задержки). Благодаря фильтрам щелчок метронома
 * из динамика не принимается за удар по струнам.
 */
class OnsetDetector(
    sampleRate: Int,
    band: Band,
    private val hop: Int = 128,
    private val ratio: Float = 4f,
    private val minEnergy: Float = 1e-5f,
    refractoryMs: Float = 70f,
) {
    enum class Band { LOW, HIGH }

    private val filters = when (band) {
        Band.LOW -> listOf(Biquad.lowPass(sampleRate, 1000.0), Biquad.lowPass(sampleRate, 1000.0))
        Band.HIGH -> listOf(Biquad.highPass(sampleRate, 3000.0), Biquad.highPass(sampleRate, 3000.0))
    }
    private val refractory = (refractoryMs / 1000f * sampleRate).toLong()
    private var position = 0L
    private var acc = 0f
    private var accCount = 0
    private var background = 0f
    private var prevEnergy = 0f
    private var lastOnset = Long.MIN_VALUE / 2

    /** Возвращает индексы сэмплов (от начала потока), где обнаружены атаки. */
    fun process(samples: FloatArray, count: Int = samples.size): List<Long> {
        val result = ArrayList<Long>(2)
        for (i in 0 until count) {
            var x = samples[i]
            for (f in filters) x = f.process(x)
            acc += x * x
            accCount++
            if (accCount == hop) {
                val energy = acc / hop
                val frameStart = position + i + 1 - hop
                val isOnset = energy > minEnergy &&
                    energy > background * ratio &&
                    energy > prevEnergy * 1.5f &&
                    frameStart - lastOnset > refractory
                if (isOnset) {
                    lastOnset = frameStart
                    result.add(frameStart)
                }
                // Медленная огибающая фона; после атаки поднимается быстро, чтобы не ловить «хвост».
                background = if (energy > background) background + (energy - background) * 0.3f
                else background + (energy - background) * 0.05f
                prevEnergy = energy
                acc = 0f
                accCount = 0
            }
        }
        position += count
        return result
    }
}

/** Биквадратный фильтр (RBJ cookbook). */
class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x.toDouble(); y2 = y1; y1 = y
        return y.toFloat()
    }

    companion object {
        private const val Q = 0.7071

        fun lowPass(sr: Int, fc: Double): Biquad {
            val w = 2 * PI * fc / sr
            val alpha = sin(w) / (2 * Q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }

        fun highPass(sr: Int, fc: Double): Biquad {
            val w = 2 * PI * fc / sr
            val alpha = sin(w) / (2 * Q)
            val c = cos(w)
            val a0 = 1 + alpha
            return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }
    }
}
