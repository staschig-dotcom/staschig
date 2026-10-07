package ru.staschig.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.staschig.guitar.audio.Notes
import ru.staschig.guitar.audio.PitchDetector
import ru.staschig.guitar.audio.Tunings
import kotlin.math.PI
import kotlin.math.sin

class PitchDetectorTest {
    private val rate = 44100
    private val size = 4096

    /** Сигнал, похожий на струну: основной тон + затухающие обертоны. */
    private fun string(freq: Float) = FloatArray(size) { i ->
        val t = i.toDouble() / rate
        (0.5 * sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t) + 0.15 * sin(6 * PI * freq * t)).toFloat()
    }

    @Test
    fun detectsAllStandardStrings() {
        val detector = PitchDetector(rate, size)
        Tunings.byId("standard").midi.forEach { midi ->
            val expected = Notes.frequency(midi)
            val f = detector.detect(string(expected))!!
            assertEquals("струна ${Notes.nameWithOctave(midi)}", 0f, Notes.cents(f, midi), 3f)
        }
    }

    @Test
    fun detectsLowDropC() {
        val detector = PitchDetector(rate, size)
        val c2 = Notes.frequency(36)
        assertEquals(0f, Notes.cents(detector.detect(string(c2))!!, 36), 3f)
    }

    @Test
    fun detectsSlightDetune() {
        val detector = PitchDetector(rate, size)
        val a2 = Notes.frequency(45) * 1.01f // ~ +17 центов
        assertEquals(17.2f, Notes.cents(detector.detect(string(a2))!!, 45), 3f)
    }

    @Test
    fun silenceIsIgnored() {
        assertNull(PitchDetector(rate, size).detect(FloatArray(size)))
    }

    @Test
    fun noteMath() {
        assertEquals(440f, Notes.frequency(69), 0.01f)
        assertEquals(82.41f, Notes.frequency(40), 0.01f)
        assertEquals("E2", Notes.nameWithOctave(40))
        assertEquals(432f, Notes.frequency(69, 432f), 0.01f)
    }
}
