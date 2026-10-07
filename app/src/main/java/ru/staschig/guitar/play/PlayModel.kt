package ru.staschig.guitar.play

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Нота таба: MIDI-высота, линия таба (0 — верхняя, 1-я струна) и лад. */
data class TabNote(val midi: Int, val line: Int, val fret: Int)

/** Удар: одна нота или аккорд в момент [timeMs] (время партитуры, при 100% темпа). */
data class NoteEvent(val timeMs: Int, val durationMs: Int, val bar: Int, val notes: List<TabNote>)

data class PlayScore(
    val title: String,
    val artist: String,
    val tempo: Double,
    val strings: Int,
    /** Долей в такте (числитель размера). */
    val beatsPerBar: Int,
    /** Длительность доли в мс: для 6/8 доля — восьмая. */
    val beatMs: Double,
    val anacrusis: Boolean,
    val bars: List<Int>,
    val durationMs: Int,
    val events: List<NoteEvent>,
    val tracks: List<String>,
    val track: Int,
) {
    fun barEnd(i: Int): Int = if (i + 1 < bars.size) bars[i + 1] else durationMs

    /** Фрагмент с такта [from] по [to] включительно; время сдвинуто к началу фрагмента. */
    fun section(from: Int, to: Int): PlayScore {
        if (from <= 0 && to >= bars.lastIndex) return this
        val start = bars[from]
        val end = barEnd(to)
        return copy(
            anacrusis = anacrusis && from == 0,
            bars = bars.subList(from, to + 1).map { it - start },
            durationMs = end - start,
            events = events.filter { it.bar in from..to }
                .map { it.copy(timeMs = it.timeMs - start, bar = it.bar - from) },
        )
    }

    companion object {
        fun parse(json: String): PlayScore {
            val o = JSONObject(json)
            if (o.has("error")) error(o.getString("error"))
            val tempo = o.optDouble("tempo", 120.0)
            val den = o.optInt("tsDen", 4)
            val evs = o.getJSONArray("events")
            val events = (0 until evs.length()).map { i ->
                val e = evs.getJSONObject(i)
                val ns = e.getJSONArray("notes")
                NoteEvent(
                    timeMs = e.getInt("t"),
                    durationMs = e.getInt("d"),
                    bar = e.getInt("bar"),
                    notes = (0 until ns.length()).map { j ->
                        val n = ns.getJSONObject(j)
                        TabNote(n.getInt("m"), n.getInt("s"), n.getInt("f"))
                    }.distinct(),
                )
            }
            val bars = o.getJSONArray("bars").let { a -> (0 until a.length()).map { a.getInt(it) } }
            val tracks = o.optJSONArray("tracks")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            return PlayScore(
                title = o.optString("title"),
                artist = o.optString("artist"),
                tempo = tempo,
                strings = o.optInt("strings", 6),
                beatsPerBar = o.optInt("ts", 4),
                beatMs = 60000.0 / tempo * 4.0 / den,
                anacrusis = o.optBoolean("anacrusis", false),
                bars = bars,
                durationMs = o.getInt("duration"),
                events = events,
                tracks = tracks,
                track = o.optInt("track", 0),
            )
        }
    }
}

/** Что услышал микрофон в один момент. */
class Detection(
    /** Дробный MIDI основного тона (YIN) или null. */
    val midi: Float?,
    /** Нормированная хромаграмма (12 классов) или null. */
    val chroma: FloatArray?,
)

/**
 * «Судья» режима игры: сопоставляет услышанное с ожидаемыми нотами.
 *
 * Правила:
 * - нота засчитывается, если звучит нужная высота (допуск ±50 центов, октавная ошибка детектора прощается)
 *   в окне [−early; +late] от её времени; аккорд — если звучит большая часть его нот;
 * - повторная та же нота требует нового удара (атаки), иначе одна долгая нота засчиталась бы дважды;
 * - в режиме ожидания время не важно — ждём, пока прозвучит текущая нота.
 */
class PlayJudge(
    val events: List<NoteEvent>,
    private val earlyMs: Double = 160.0,
    private val lateMs: Double = 260.0,
) {
    enum class State { PENDING, HIT, MISSED }

    val states = Array(events.size) { State.PENDING }
    /** Отклонение удара от ноты, мс партитуры (+ поздно, − рано). */
    val deviations = DoubleArray(events.size)

    private var lastOnsetMs = Double.NEGATIVE_INFINITY
    private var onsetUsed = true
    private var lastHitPitches: Set<Int> = emptySet()

    val hits: Int get() = states.count { it == State.HIT }
    val missed: Int get() = states.count { it == State.MISSED }
    val resolved: Int get() = states.count { it != State.PENDING }

    /** Первая ещё не сыгранная нота (для режима ожидания и подсветки). */
    fun nextPending(): Int = states.indexOfFirst { it == State.PENDING }

    fun onOnset(timeMs: Double) {
        lastOnsetMs = timeMs
        onsetUsed = false
    }

    /**
     * Кадр детектора в момент [timeMs] (время партитуры). Возвращает индекс засчитанной ноты или −1.
     * [waitIndex] ≥ 0 — режим ожидания: проверяется только эта нота, без окна по времени.
     */
    fun onFrame(timeMs: Double, d: Detection, waitIndex: Int = -1): Int {
        val candidates = if (waitIndex >= 0) listOf(waitIndex) else events.indices.filter { i ->
            states[i] == State.PENDING && timeMs - events[i].timeMs in -earlyMs..lateMs
        }
        for (i in candidates) {
            if (i !in events.indices || states[i] != State.PENDING) continue
            val ev = events[i]
            if (!matches(ev, d)) continue
            val pitches = ev.notes.map { it.midi }.toSet()
            val samePitch = pitches == lastHitPitches
            if (samePitch) {
                // Та же нота подряд — нужен свежий удар, пришедший не раньше окна этой ноты.
                val fresh = !onsetUsed && (waitIndex >= 0 || lastOnsetMs >= ev.timeMs - earlyMs)
                if (!fresh) continue
            }
            states[i] = State.HIT
            deviations[i] = if (waitIndex >= 0) 0.0 else timeMs - ev.timeMs
            lastHitPitches = pitches
            onsetUsed = true
            return i
        }
        return -1
    }

    /** Ноты, окно которых прошло, считаются пропущенными. */
    fun expire(nowMs: Double): List<Int> {
        val out = ArrayList<Int>()
        for (i in events.indices) {
            if (states[i] == State.PENDING && nowMs - events[i].timeMs > lateMs) {
                states[i] = State.MISSED
                out += i
            }
        }
        return out
    }

    companion object {
        fun matches(ev: NoteEvent, d: Detection): Boolean {
            val pitches = ev.notes.map { it.midi }.distinct()
            if (pitches.size == 1) {
                val target = pitches[0]
                val m = d.midi
                if (m != null) {
                    val diff = m - target
                    // Детектор иногда ошибается на октаву — прощаем.
                    if (abs(diff) <= 0.5f || abs(abs(diff) - 12f) <= 0.5f) return true
                }
                // Нота поверх звучащего баса: YIN может «слышать» бас, тогда смотрим хромаграмму.
                // Если же YIN уверенно слышит соседнюю ноту — это просто не та нота.
                if (m != null && m > target - 7f) return false
                val c = d.chroma ?: return false
                val max = c.max()
                if (max <= 0f) return false
                val pc = target % 12
                val rank = c.indices.sortedByDescending { c[it] }.indexOf(pc)
                return rank <= 1 && c[pc] >= 0.6f * max
            }
            // Аккорд или двойная нота: должно звучать ≥ 2/3 разных нот.
            val c = d.chroma ?: return false
            val max = c.max()
            if (max <= 0f) return false
            val pcs = pitches.map { it % 12 }.distinct()
            val present = pcs.count { c[it] >= 0.35f * max }
            return present * 3 >= pcs.size * 2
        }
    }
}

/** Итог прохождения: точность, звёзды, опыт. */
data class PlayResult(
    val total: Int,
    val hits: Int,
    val accuracy: Int,
    val stars: Int,
    val meanDeviationMs: Int,
    val xp: Int,
) {
    companion object {
        fun of(judge: PlayJudge, speedPercent: Int, waitMode: Boolean): PlayResult {
            val total = judge.events.size
            val hits = judge.hits
            val accuracy = if (total == 0) 0 else (100.0 * hits / total).roundToInt()
            val stars = stars(accuracy)
            val devs = judge.events.indices.filter { judge.states[it] == PlayJudge.State.HIT }.map { judge.deviations[it] }
            val mean = if (devs.isEmpty() || waitMode) 0 else devs.average().roundToInt()
            // Опыт: за каждую ноту, с учётом скорости; режим ожидания — вполовину (он проще).
            val base = hits * 10 * speedPercent / 100 + stars * 25
            return PlayResult(total, hits, accuracy, stars, mean, if (waitMode) base / 2 else base)
        }

        fun stars(accuracy: Int) = when {
            accuracy >= 95 -> 3
            accuracy >= 80 -> 2
            accuracy >= 60 -> 1
            else -> 0
        }

        /** Уровень игрока по опыту: каждый следующий уровень дороже на 100 XP. */
        fun level(xp: Int): Triple<Int, Int, Int> {
            var level = 1
            var need = 300
            var left = xp
            while (left >= need) {
                left -= need
                level++
                need += 100
            }
            return Triple(level, left, need)
        }
    }
}
