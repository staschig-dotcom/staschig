package ru.staschig.guitar.audio

import kotlin.random.Random

/** Синтез гитарной струны алгоритмом Карплуса — Стронга. */
object GuitarSynth {
    fun render(
        midiNotes: List<Int>,
        sampleRate: Int = 44100,
        seconds: Float = 2.5f,
        strumMs: Float = 18f,
        seed: Int = 7,
        detuneCents: Float = 0f,
    ): FloatArray {
        val n = (sampleRate * seconds).toInt()
        val out = FloatArray(n)
        val rnd = Random(seed)
        midiNotes.forEachIndexed { idx, midi ->
            val f = Notes.frequency(midi) * Math.pow(2.0, detuneCents / 1200.0).toFloat()
            val period = (sampleRate / f).toInt().coerceAtLeast(2)
            val buf = FloatArray(period) { rnd.nextFloat() * 2f - 1f }
            val start = (idx * strumMs / 1000f * sampleRate).toInt()
            var p = 0
            for (i in start until n) {
                val next = (p + 1) % period
                val v = buf[p]
                buf[p] = 0.996f * 0.5f * (buf[p] + buf[next])
                out[i] += v
                p = next
            }
        }
        var peak = 0f
        for (v in out) peak = maxOf(peak, kotlin.math.abs(v))
        if (peak > 0f) for (i in out.indices) out[i] = out[i] / peak * 0.8f
        return out
    }
}
