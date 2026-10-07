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
