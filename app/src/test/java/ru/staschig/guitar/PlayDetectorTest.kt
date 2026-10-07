package ru.staschig.guitar

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.play.PlayDetector
import ru.staschig.guitar.play.PlayJudge
import ru.staschig.guitar.play.PlayScore
import kotlin.random.Random

/** Сквозная проверка: синтезированная гитара → детектор → судья. */
class PlayDetectorTest {
    private val sr = 44100

    private fun load(name: String) = PlayScore.parse(javaClass.getResource("/play/$name.json")!!.readText())

    /** Играем партию, как гитарист: каждая нота — щипок струны; [wrong] — индексы, где сыграна чужая нота. */
    private fun perform(score: PlayScore, wrong: Set<Int> = emptySet(), jitterMs: Int = 30): FloatArray {
        val out = FloatArray(((score.durationMs + 1500) / 1000.0 * sr).toInt())
        val rnd = Random(3)
        score.events.forEachIndexed { i, ev ->
            val notes = ev.notes.map { if (i in wrong) it.midi + 2 else it.midi }
            val sound = GuitarSynth.render(notes, sr, seconds = ev.durationMs / 1000f + 0.05f, seed = i)
            val start = ((ev.timeMs + rnd.nextInt(-jitterMs, jitterMs + 1)) / 1000.0 * sr).toInt()
            for (k in sound.indices) if (start + k in out.indices) out[start + k] += sound[k] * 0.5f
        }
        for (k in out.indices) out[k] += (rnd.nextFloat() * 2 - 1) * 0.003f // шум комнаты
        return out
    }

    private fun judge(score: PlayScore, audio: FloatArray): PlayJudge {
        val det = PlayDetector(sr)
        val judge = PlayJudge(score.events)
        var i = 0
        while (i + 1024 <= audio.size) {
            val frame = det.process(audio.copyOfRange(i, i + 1024))
            frame.onsets.forEach { judge.onOnset(it * 1000.0 / sr) }
            judge.onFrame(frame.sample * 1000.0 / sr, frame.detection)
            judge.expire(frame.sample * 1000.0 / sr)
            i += 1024
        }
        judge.expire(Double.MAX_VALUE)
        return judge
    }

    @Test
    fun cleanMelodyScoresHigh() {
        val score = load("ode_to_joy")
        val j = judge(score, perform(score))
        println("ode clean: ${j.hits}/${score.events.size}")
        assertTrue("засчитано ${j.hits} из ${score.events.size}", j.hits >= score.events.size * 0.9)
    }

    @Test
    fun wrongNotesAreNotCounted() {
        val score = load("ode_to_joy")
        val wrong = setOf(2, 5, 9, 14, 20, 27)
        val j = judge(score, perform(score, wrong))
        val falseHits = wrong.count { j.states[it] == PlayJudge.State.HIT }
        println("ode wrong: hits ${j.hits}, false hits $falseHits")
        assertTrue("ложно засчитано $falseHits", falseHits <= 1)
        assertTrue(j.hits <= score.events.size - wrong.size + 1)
    }

    @Test
    fun greensleevesWithPickup() {
        val score = load("greensleeves")
        val j = judge(score, perform(score))
        println("greensleeves: ${j.hits}/${score.events.size}")
        assertTrue(j.hits >= score.events.size * 0.85)
    }

    @Test
    fun silenceScoresZero() {
        val score = load("ode_to_joy")
        val j = judge(score, FloatArray(score.durationMs * sr / 1000))
        assertTrue(j.hits == 0)
    }
}
