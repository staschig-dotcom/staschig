package ru.staschig.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.staschig.guitar.audio.ChordRecognizer
import ru.staschig.guitar.audio.Fft
import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.audio.OnsetDetector
import ru.staschig.guitar.audio.RhythmClick
import ru.staschig.guitar.audio.RhythmScorer
import ru.staschig.guitar.lessons.Chords
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class DspTest {
    private val sr = 44100

    @Test
    fun fftFindsSinePeak() {
        val n = 4096
        val f = 1000f
        val mag = Fft.magnitudes(FloatArray(n) { sin(2 * PI * f * it / sr).toFloat() })
        val peak = mag.indices.maxBy { mag[it] }
        assertEquals((f * n / sr).toInt().toFloat(), peak.toFloat(), 1f)
    }

    @Test
    fun recognizesOpenChords() {
        val rec = ChordRecognizer(sr, Chords.recognizable)
        val names = listOf("A", "Am", "C", "D", "Dm", "E", "Em", "G", "F", "Bm", "E7", "A7")
        val wrong = names.filter { name ->
            val chord = Chords.byName(name)!!
            val audio = GuitarSynth.render(chord.midi, sr, seconds = 1f)
            // окно 8192 после атаки, как в приложении
            val window = audio.copyOfRange(4410, 4410 + 8192)
            val top = rec.match(window).first().chord.name
            if (top != name) println("$name -> $top")
            top != name
        }
        assertTrue("не распознаны: $wrong", wrong.size <= 2)
    }

    @Test
    fun detunedGuitarStillRecognized() {
        val rec = ChordRecognizer(sr, Chords.recognizable)
        for (cents in listOf(-15f, 15f)) for (name in listOf("Am", "D", "G", "E")) {
            val audio = GuitarSynth.render(Chords.byName(name)!!.midi, sr, seconds = 1f, detuneCents = cents)
            assertEquals("$name $cents", name, rec.match(audio.copyOfRange(4410, 4410 + 8192)).first().chord.name)
        }
    }

    @Test
    fun majorVsMinorDistinct() {
        val rec = ChordRecognizer(sr, listOf(Chords.byName("A")!!, Chords.byName("Am")!!,
            Chords.byName("E")!!, Chords.byName("Em")!!))
        for (name in listOf("A", "Am", "E", "Em")) {
            val audio = GuitarSynth.render(Chords.byName(name)!!.midi, sr, seconds = 1f)
            assertEquals(name, rec.match(audio.copyOfRange(4410, 4410 + 8192)).first().chord.name)
        }
    }

    private fun mix(lenSec: Double, strums: List<Double>, clicks: List<Double>): FloatArray {
        val n = (lenSec * sr).toInt()
        val out = FloatArray(n)
        val rnd = Random(1)
        for (t in strums) {
            val s = (t * sr).toInt()
            for (i in 0 until (0.15 * sr).toInt()) if (s + i < n) {
                out[s + i] += (rnd.nextFloat() * 2 - 1) * 0.5f * exp(-i / (0.03 * sr)).toFloat()
            }
        }
        for (t in clicks) {
            val s = (t * sr).toInt()
            for (i in RhythmClick.samples(sr).indices) if (s + i < n) {
                out[s + i] += RhythmClick.samples(sr)[i]
            }
        }
        for (i in out.indices) out[i] += (rnd.nextFloat() * 2 - 1) * 0.002f
        return out
    }

    private fun detect(audio: FloatArray, band: OnsetDetector.Band): List<Double> {
        val d = OnsetDetector(sr, band)
        val res = ArrayList<Long>()
        var i = 0
        while (i < audio.size) {
            val chunk = audio.copyOfRange(i, minOf(audio.size, i + 1024))
            res += d.process(chunk)
            i += 1024
        }
        return res.map { it.toDouble() / sr }
    }

    @Test
    fun lowBandHearsStrumsNotClicks() {
        val strums = listOf(0.5, 1.0, 1.52, 2.0)
        val clicks = listOf(0.25, 0.75, 1.25, 1.75, 2.25)
        val found = detect(mix(2.6, strums, clicks), OnsetDetector.Band.LOW)
        assertEquals(strums.size, found.size)
        strums.zip(found).forEach { (e, a) -> assertEquals(e, a, 0.006) }
    }

    @Test
    fun highBandHearsClicks() {
        val clicks = listOf(0.25, 0.75, 1.25, 1.75, 2.25)
        val found = detect(mix(2.6, emptyList(), clicks), OnsetDetector.Band.HIGH)
        assertEquals(clicks.size, found.size)
        clicks.zip(found).forEach { (e, a) -> assertEquals(e, a, 0.006) }
    }

    @Test
    fun scorerReportsRushingAndMisses() {
        val ticks = (0 until 8).map { it * 500.0 }
        val onsets = ticks.take(7).map { it - 20 } + listOf(3200.0)
        val r = RhythmScorer.score(ticks, onsets)
        assertEquals(-20.0, r.meanMs, 0.01)
        assertEquals(1, r.missed)
        assertEquals(1, r.extra)
        assertTrue(r.verdict.startsWith("Спешите"))
    }

    @Test
    fun latencyIsMedian() {
        val clicks = (0 until 10).map { it * 500.0 }
        val heard = clicks.map { it + 120 } + listOf(1300.0)
        assertNotNull(RhythmScorer.latency(clicks, heard))
        assertEquals(120.0, RhythmScorer.latency(clicks, heard)!!, 0.01)
    }
}
