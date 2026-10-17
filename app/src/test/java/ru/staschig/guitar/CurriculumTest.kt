package ru.staschig.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Focus
import ru.staschig.guitar.lessons.Level
import ru.staschig.guitar.lessons.StepKind
import ru.staschig.guitar.lessons.StepTool
import ru.staschig.guitar.lessons.Chords

class CurriculumTest {
    @Test
    fun everyLevelHasLessons() {
        Level.entries.forEach { assertTrue(Curriculum.byLevel(it).size >= 8) }
    }

    @Test
    fun idsAreUnique() {
        val ids = Curriculum.lessons.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun eachLessonHasWarmupTechniqueAndSong() {
        Curriculum.lessons.forEach { lesson ->
            val kinds = lesson.steps(20, Focus.BALANCED).map { it.kind }
            assertEquals(listOf(StepKind.WARMUP, StepKind.TECHNIQUE, StepKind.SONG), kinds)
        }
    }

    @Test
    fun stepDurationsFollowDailyTime() {
        Focus.entries.forEach { focus ->
            listOf(10, 20, 30, 60).forEach { minutes ->
                val total = Curriculum.lessons.first().steps(minutes, focus).sumOf { it.minutes }
                assertTrue("$focus $minutes -> $total", kotlin.math.abs(total - minutes) <= 1)
            }
        }
    }
}

class StepToolTest {
    @Test
    fun songsOpenTabsAndChordDrillsUseKnownChords() {
        Curriculum.lessons.forEach { lesson ->
            val steps = lesson.steps(20, Focus.BALANCED)
            assertEquals(StepTool.SongTabs, steps.last().tool)
            steps.map { it.tool }.filterIsInstance<StepTool.ChordChanges>().forEach { t ->
                t.pairs.forEach { (a, b) ->
                    assertTrue(a, Chords.recognizable.any { it.name == a })
                    assertTrue(b, Chords.recognizable.any { it.name == b })
                }
            }
        }
    }

    @Test
    fun rhythmExercisesOpenMetronomeAtTheirTempo() {
        val strum = Curriculum.lessons.first { it.technique.id == "t_strum" }.steps(20, Focus.BALANCED)[1]
        assertEquals(StepTool.Metronome(60, 90), strum.tool)
    }
}

/** Замечания тестировщика: песни не должны опережать программу. */
class CourseOrderTest {
    /** Какие аккорды даёт каждое упражнение начального уровня. */
    private val taught = mapOf(
        "t_ade" to setOf("A", "D", "E"),
        "t_emcg" to setOf("Am", "Em", "C", "G"),
        "t_power" to setOf("E5", "A5", "D5", "G5", "F5", "Bb5", "Ab5", "Db5"),
        "t_fmaj7" to setOf("Fmaj7", "F"),
    )

    @Test
    fun beginnerSongsUseOnlyChordsAlreadyTaught() {
        val known = mutableSetOf<String>()
        Curriculum.byLevel(Level.BEGINNER).forEach { lesson ->
            known += taught[lesson.technique.id].orEmpty()
            val unknown = lesson.song.chords.filter { it !in known }
            assertTrue("Урок ${lesson.number} «${lesson.song.title}»: аккорды $unknown ещё не изучены", unknown.isEmpty())
        }
    }

    @Test
    fun firstLessonIsBasicsWithoutChords() {
        val first = Curriculum.byLevel(Level.BEGINNER).first()
        assertEquals("t_posture", first.technique.id)
        assertTrue(first.song.chords.isEmpty())
        // Разминка без гитары — ни таба, ни метронома.
        assertEquals(StepTool.TimerOnly, first.steps(20, Focus.BALANCED)[0].tool)
        assertTrue(first.warmup.tab == null)
    }

    @Test
    fun everyLessonExerciseAndSongExist() {
        Level.entries.forEach { assertEquals(8, Curriculum.byLevel(it).size) }
        assertEquals(Curriculum.lessons.size, Curriculum.lessons.map { it.song.id }.toSet().size)
    }

    @Test
    fun chordFingeringsAreConsistent() {
        Chords.all.forEach { c ->
            c.frets.forEachIndexed { i, f ->
                val finger = c.fingers[i]
                if (f == null || f == 0) assertEquals("${c.name}: струна ${6 - i}", 0, finger)
                else assertTrue("${c.name}: нет пальца на струне ${6 - i}", finger in 1..5)
            }
        }
        assertEquals(Triple(1, 0, 5), Chords.byName("F")!!.barre)
        assertEquals(null, Chords.byName("C")!!.barre)
    }
}
