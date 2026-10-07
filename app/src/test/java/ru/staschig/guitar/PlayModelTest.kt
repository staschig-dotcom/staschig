package ru.staschig.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.staschig.guitar.audio.Notes
import ru.staschig.guitar.play.Detection
import ru.staschig.guitar.play.PlayJudge
import ru.staschig.guitar.play.PlayResult
import ru.staschig.guitar.play.PlayScore

class PlayModelTest {
    private fun load(name: String): PlayScore =
        PlayScore.parse(javaClass.getResource("/play/$name.json")!!.readText())

    private fun note(midi: Int) = Detection(midi.toFloat(), null)

    @Test
    fun parsesRealAlphaTabOutput() {
        val ode = load("ode_to_joy")
        assertEquals(30, ode.events.size)
        assertEquals(64, ode.events.first().notes.single().midi)
        assertEquals(0, ode.events.first().notes.single().line) // 1-я струна — верхняя линия
        assertEquals(4, ode.beatsPerBar)
        val gs = load("greensleeves")
        assertTrue(gs.anacrusis)
        assertEquals(3, gs.beatsPerBar)
        assertEquals(600.0, gs.beatMs, 0.5)
    }

    @Test
    fun perfectPlayGetsThreeStars() {
        val score = load("ode_to_joy")
        val judge = PlayJudge(score.events)
        score.events.forEach { ev ->
            judge.onOnset(ev.timeMs + 5.0)
            assertTrue(judge.onFrame(ev.timeMs + 40.0, note(ev.notes[0].midi)) >= 0)
        }
        judge.expire(score.durationMs + 1000.0)
        val r = PlayResult.of(judge, 100, waitMode = false)
        assertEquals(100, r.accuracy)
        assertEquals(3, r.stars)
        assertEquals(40, r.meanDeviationMs)
    }

    @Test
    fun repeatedNoteNeedsNewAttack() {
        val score = load("ode_to_joy") // начинается с E E
        val judge = PlayJudge(score.events)
        judge.onOnset(0.0)
        assertEquals(0, judge.onFrame(20.0, note(64)))
        // Первая E продолжает звучать во время второй, нового удара нет — не засчитываем.
        assertEquals(-1, judge.onFrame(score.events[1].timeMs + 10.0, note(64)))
        judge.onOnset(score.events[1].timeMs + 15.0)
        assertEquals(1, judge.onFrame(score.events[1].timeMs + 30.0, note(64)))
    }

    @Test
    fun wrongNoteAndSilenceAreMissed() {
        val score = load("ode_to_joy")
        val judge = PlayJudge(score.events)
        judge.onOnset(0.0)
        assertEquals(-1, judge.onFrame(10.0, note(65))) // F вместо E
        judge.expire(score.durationMs + 1000.0)
        assertEquals(0, judge.hits)
        assertEquals(score.events.size, judge.missed)
        assertEquals(0, PlayResult.of(judge, 100, false).stars)
    }

    @Test
    fun octaveErrorIsForgivenButSemitoneIsNot() {
        val ev = load("ode_to_joy").events[0]
        assertTrue(PlayJudge.matches(ev, note(52)))
        assertTrue(PlayJudge.matches(ev, Detection(64.4f, null)))
        assertFalse(PlayJudge.matches(ev, Detection(64.7f, null)))
    }

    @Test
    fun melodyOverBassUsesChroma() {
        // Романс: B4 (мелодия) + E2 (бас) одновременно — это двойная нота, проверяется по хромаграмме.
        val ev = load("romance").events[0]
        assertEquals(2, ev.notes.size)
        val chroma = FloatArray(12).also { it[Notes.nearestMidi(Notes.frequency(71)) % 12] = 1f; it[4] = 0.9f }
        assertTrue(PlayJudge.matches(ev, Detection(40f, chroma)))
    }

    @Test
    fun waitModeIgnoresTiming() {
        val score = load("ode_to_joy")
        val judge = PlayJudge(score.events)
        judge.onOnset(99_000.0)
        assertEquals(0, judge.onFrame(99_000.0, note(64), waitIndex = 0))
        assertEquals(1, judge.nextPending())
    }

    @Test
    fun sectionShiftsTimes() {
        val gs = load("greensleeves")
        val s = gs.section(1, 4)
        assertFalse(s.anacrusis)
        assertEquals(0, s.events.first().timeMs)
        assertEquals(60, s.events.first().notes[0].midi) // C — первая нота 1-го полного такта
        assertTrue(s.events.all { it.bar in 0..3 })
    }

    @Test
    fun levels() {
        assertEquals(1, PlayResult.level(0).first)
        assertEquals(2, PlayResult.level(300).first)
        assertEquals(3, PlayResult.level(700).first)
    }
}
