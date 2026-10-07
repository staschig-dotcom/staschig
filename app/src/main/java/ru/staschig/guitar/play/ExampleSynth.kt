package ru.staschig.guitar.play

import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.lessons.Chord
import kotlin.math.abs

/**
 * «Как должно звучать»: синтез примера гитарным звуком (Карплус — Стронг).
 * Чистый Kotlin — тестируется без Android.
 */
object ExampleSynth {
    const val SAMPLE_RATE = 44100
    private const val MAX_SECONDS = 90

    /** Ноты партии со скоростью [speed] (1.0 = оригинальный темп). Длина ограничена 90 с. */
    fun renderNotes(events: List<NoteEvent>, speed: Double = 1.0, sampleRate: Int = SAMPLE_RATE): FloatArray {
        if (events.isEmpty()) return FloatArray(0)
        val endMs = events.maxOf { it.timeMs + it.durationMs } / speed + 1200
        val n = (minOf(endMs / 1000.0, MAX_SECONDS.toDouble()) * sampleRate).toInt()
        val out = FloatArray(n)
        events.forEachIndexed { i, ev ->
            val start = (ev.timeMs / speed / 1000.0 * sampleRate).toInt()
            if (start >= n) return@forEachIndexed
            // Нота звучит свою длительность плюс немного «хвоста», но не дольше 2 с.
            val seconds = minOf(2f, (ev.durationMs / speed / 1000.0).toFloat() + 0.25f)
            val sound = GuitarSynth.render(ev.notes.map { it.midi }.sorted(), sampleRate, seconds, strumMs = 12f, seed = i)
            mix(out, sound, start, fadeLastMs = 40, sampleRate = sampleRate)
        }
        return normalize(out)
    }

    /**
     * Аккорды песни по тактам: на каждую долю удар вниз ([pattern] по восьмым: 'D' — вниз, 'U' — вверх, '.' — пропуск).
     */
    fun renderChords(
        chords: List<Chord>,
        bpm: Int,
        beatsPerChord: Int = 4,
        pattern: String = "D.DUD.DU",
        sampleRate: Int = SAMPLE_RATE,
    ): FloatArray {
        if (chords.isEmpty()) return FloatArray(0)
        val eighthSamples = (60.0 / bpm / 2 * sampleRate).toInt()
        val perChord = beatsPerChord * 2
        val n = minOf(chords.size * perChord * eighthSamples + sampleRate, MAX_SECONDS * sampleRate)
        val out = FloatArray(n)
        chords.forEachIndexed { ci, chord ->
            for (k in 0 until perChord) {
                val stroke = pattern[k % pattern.length]
                if (stroke == '.') continue
                val start = (ci * perChord + k) * eighthSamples
                if (start >= n) break
                val notes = if (stroke == 'U') chord.midi.sortedDescending().take(4) else chord.midi.sorted()
                val gain = if (stroke == 'U') 0.6f else 1f
                val sound = GuitarSynth.render(notes, sampleRate, seconds = 1.2f, strumMs = 9f, seed = ci * 31 + k)
                for (i in sound.indices) sound[i] *= gain
                // Новый удар глушит предыдущий — как на настоящей гитаре.
                val end = minOf(n, start + sound.size)
                for (i in start until end) out[i] *= 0.15f
                mix(out, sound, start, fadeLastMs = 20, sampleRate = sampleRate)
            }
        }
        return normalize(out)
    }

    private fun mix(out: FloatArray, sound: FloatArray, start: Int, fadeLastMs: Int, sampleRate: Int) {
        val fade = fadeLastMs * sampleRate / 1000
        for (i in sound.indices) {
            val j = start + i
            if (j >= out.size) break
            val f = if (i > sound.size - fade) (sound.size - i).toFloat() / fade else 1f
            out[j] += sound[i] * f
        }
    }

    private fun normalize(out: FloatArray): FloatArray {
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        if (peak > 0f) for (i in out.indices) out[i] = out[i] / peak * 0.85f
        return out
    }
}
