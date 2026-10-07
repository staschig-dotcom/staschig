package ru.staschig.guitar.audio

import ru.staschig.guitar.lessons.Chord
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Распознавание аккорда по хромаграмме (12 классов высоты).
 * Шаблон аккорда строится из реальных нот аппликатуры с учётом обертонов —
 * так мажор и минор различаются заметно лучше, чем на «голых» трезвучиях.
 */
class ChordRecognizer(
    private val sampleRate: Int,
    candidates: List<Chord>,
) {
    data class Match(val chord: Chord, val score: Float)

    private val templates = candidates.map { it to normalize(template(it)) }

    fun chroma(samples: FloatArray): FloatArray {
        val mag = Fft.magnitudes(samples)
        val n = samples.size
        val minBin = (70f * n / sampleRate).toInt().coerceAtLeast(2)
        val maxBin = (1000f * n / sampleRate).toInt().coerceAtMost(mag.size - 2)
        // Пики спектра с параболическим уточнением частоты.
        val midis = ArrayList<Float>()
        val weights = ArrayList<Float>()
        for (k in minBin..maxBin) {
            if (mag[k] <= mag[k - 1] || mag[k] < mag[k + 1]) continue
            val a = mag[k - 1]; val b = mag[k]; val g = mag[k + 1]
            val denom = a - 2 * b + g
            val p = if (denom != 0f) 0.5f * (a - g) / denom else 0f
            midis += Notes.midiOf((k + p) * sampleRate / n)
            weights += sqrt(b)
        }
        if (midis.isEmpty()) return FloatArray(12)
        // Общая расстройка инструмента (гитара может быть настроена чуть ниже/выше A440).
        val strongest = midis.indices.sortedByDescending { weights[it] }.take(12)
        val devs = strongest.map { midis[it] - midis[it].roundToInt() }.sorted()
        val offset = devs[devs.size / 2]
        val chroma = FloatArray(12)
        for (i in midis.indices) {
            val m = midis[i] - offset
            val r = m.roundToInt()
            // Пики далеко от темперированной ноты — это обертоны вроде 7-го или шум.
            if (abs(m - r) > CENTS_TOLERANCE / 100f) continue
            chroma[((r % 12) + 12) % 12] += weights[i]
        }
        return normalize(chroma)
    }

    fun match(samples: FloatArray): List<Match> = matchChroma(chroma(samples))

    fun matchChroma(chroma: FloatArray): List<Match> =
        templates.map { (c, t) -> Match(c, dot(chroma, t) - complexityPenalty(c)) }
            .sortedByDescending { it.score }

    companion object {
        private const val CENTS_TOLERANCE = 20f

        /** Небольшой штраф за «лишние» ноты: при равенстве побеждает простой аккорд, а не септаккорд. */
        private fun complexityPenalty(c: Chord): Float =
            0.02f * (c.midi.map { it % 12 }.toSet().size - 3).coerceAtLeast(0)

        private val harmonicWeights = floatArrayOf(1f, 0.7f, 0.45f, 0.35f, 0.2f, 0.15f)

        fun template(chord: Chord): FloatArray {
            val t = FloatArray(12)
            chord.midi.forEach { note ->
                harmonicWeights.forEachIndexed { i, w ->
                    val h = i + 1
                    val shift = (12 * ln(h.toDouble()) / ln(2.0)).roundToInt()
                    t[(note + shift) % 12] += w
                }
            }
            return t
        }

        fun normalize(v: FloatArray): FloatArray {
            val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
            return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
        }

        private fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}
