package ru.staschig.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.staschig.guitar.lessons.Chords
import ru.staschig.guitar.play.ExampleSynth
import ru.staschig.guitar.play.PlayDetector
import ru.staschig.guitar.play.PlayJudge
import ru.staschig.guitar.play.PlayScore

class ExampleSynthTest {
    private fun load(name: String) = PlayScore.parse(javaClass.getResource("/play/$name.json")!!.readText())

    @Test
    fun exampleIsRecognizedByOwnJudge() {
        // Пример должен звучать так, что собственный «судья» засчитал бы его почти целиком.
        val score = load("ode_to_joy")
        val audio = ExampleSynth.renderNotes(score.events)
        val det = PlayDetector()
        val judge = PlayJudge(score.events)
        var i = 0
        while (i + 1024 <= audio.size) {
            val f = det.process(audio.copyOfRange(i, i + 1024))
            f.onsets.forEach { judge.onOnset(it * 1000.0 / 44100) }
            val t = f.sample * 1000.0 / 44100
            judge.onFrame(t, f.detection)
            judge.expire(t)
            i += 1024
        }
        judge.expire(Double.MAX_VALUE)
        assertTrue("засчитано ${judge.hits}/${score.events.size}", judge.hits >= score.events.size - 2)
    }

    @Test
    fun slowerExampleIsLonger() {
        val events = load("ode_to_joy").events
        val normal = ExampleSynth.renderNotes(events, 1.0).size
        val slow = ExampleSynth.renderNotes(events, 0.5).size
        assertTrue(slow > normal * 1.7)
    }

    @Test
    fun chordProgressionLength() {
        val chords = listOf("G", "D", "Am", "C").map { Chords.byName(it)!! }
        val audio = ExampleSynth.renderChords(chords, bpm = 120)
        // 4 аккорда по такту 4/4 при 120 BPM = 8 с (+1 с хвост)
        assertEquals(9.0, audio.size / 44100.0, 0.05)
        assertTrue(audio.any { it != 0f })
    }
}
