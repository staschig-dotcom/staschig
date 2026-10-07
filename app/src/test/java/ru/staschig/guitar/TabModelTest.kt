package ru.staschig.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.staschig.guitar.tabedit.EdBeat
import ru.staschig.guitar.tabedit.EdNote
import ru.staschig.guitar.tabedit.EdScore
import ru.staschig.guitar.tabedit.NoteEffect
import java.io.File

class TabModelTest {
    /** Риф из упражнения + аккорд + пауза + все приёмы. */
    private fun sample() = EdScore(
        title = "Проверка \"кавычек\"",
        artist = "Я",
        tempo = 100,
        beats = listOf(
            EdBeat(8, notes = listOf(EdNote(6, 0))),
            EdBeat(8, notes = listOf(EdNote(6, 3, NoteEffect.HAMMER))),
            EdBeat(8, notes = listOf(EdNote(6, 5))),
            EdBeat(8, notes = listOf(EdNote(5, 2, NoteEffect.SLIDE))),
            EdBeat(4, notes = listOf(EdNote(5, 4))),
            EdBeat(4, dotted = false),
            // такт 2: аккорд Am на половину, бенд, глухая, вибрато
            EdBeat(2, notes = listOf(EdNote(5, 0), EdNote(4, 2), EdNote(3, 2), EdNote(2, 1), EdNote(1, 0))),
            EdBeat(8, notes = listOf(EdNote(3, 7, NoteEffect.BEND))),
            EdBeat(8, notes = listOf(EdNote(6, 0, NoteEffect.DEAD))),
            EdBeat(4, notes = listOf(EdNote(1, 5, NoteEffect.VIBRATO), EdNote(6, 0, NoteEffect.PALM_MUTE))),
            // такт 3: точка
            EdBeat(2, dotted = true, notes = listOf(EdNote(1, 12))),
            EdBeat(4),
        ),
    )

    @Test
    fun barsFollowTimeSignature() {
        val bars = sample().bars()
        assertEquals(3, bars.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), bars[0])
        assertEquals(listOf(10, 11), bars[2])
    }

    @Test
    fun overflowingBeatMovesToNextBar() {
        val s = EdScore(beats = listOf(EdBeat(2), EdBeat(4), EdBeat(2)))
        assertEquals(listOf(listOf(0, 1), listOf(2)), s.bars())
    }

    @Test
    fun jsonRoundTrip() {
        val s = sample()
        assertEquals(s, EdScore.fromJson(s.toJson()))
    }

    @Test
    fun alphaTexLooksRight() {
        val tex = sample().toAlphaTex()
        assertTrue(tex.contains("\\tuning E4 B3 G3 D3 A2 E2"))
        assertTrue(tex.contains("(3.6{h}).8"))
        assertTrue(tex.contains("r.4"))
        assertTrue(tex.contains("(0.5 2.4 2.3 1.2 0.1).2") || tex.contains("(0.1 1.2 2.3 2.4 0.5).2"))
        assertTrue(tex.contains("(12.1).2{d}"))
        assertTrue(!tex.contains("\"кавычек\""))
        // Для проверки рендера в Chromium (см. scratchpad) — кладём рядом с тестами.
        File(System.getProperty("java.io.tmpdir"), "editor_sample.alphatex").writeText(tex)
    }
}
