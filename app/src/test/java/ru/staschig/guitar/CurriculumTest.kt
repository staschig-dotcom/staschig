package ru.staschig.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Focus
import ru.staschig.guitar.lessons.Level
import ru.staschig.guitar.lessons.StepKind

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
