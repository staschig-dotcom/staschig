package ru.staschig.guitar.tabedit

import org.json.JSONArray
import org.json.JSONObject

/** Приём на ноте (как в Guitar Pro). [tex] — обозначение в alphaTex. */
enum class NoteEffect(val title: String, val short: String, val tex: String) {
    NONE("Обычная", "", ""),
    HAMMER("Hammer-on / pull-off", "h", "h"),
    SLIDE("Слайд", "sl", "sl"),
    BEND("Бенд на тон", "b", "b (0 4)"),
    VIBRATO("Вибрато", "~", "v"),
    PALM_MUTE("Глушение ладонью", "PM", "pm"),
    DEAD("Глухая нота", "x", ""),
}

/** Нота: струна 1 — верхняя (тонкая e), лад 0 — открытая. */
data class EdNote(val string: Int, val fret: Int, val effect: NoteEffect = NoteEffect.NONE)

/** Удар (доля): длительность 1/2/4/8/16/32, точка; без нот — пауза. */
data class EdBeat(
    val duration: Int = 4,
    val dotted: Boolean = false,
    val notes: List<EdNote> = emptyList(),
) {
    val isRest: Boolean get() = notes.isEmpty()

    /** Длина в тиках (960 на четверть). */
    val ticks: Int get() = (3840 / duration).let { if (dotted) it * 3 / 2 else it }

    fun noteOn(string: Int): EdNote? = notes.firstOrNull { it.string == string }

    fun withNote(note: EdNote): EdBeat = copy(notes = (notes.filter { it.string != note.string } + note).sortedBy { it.string })

    fun withoutNote(string: Int): EdBeat = copy(notes = notes.filter { it.string != string })
}

/** Таб, созданный вручную в приложении. */
data class EdScore(
    val title: String = "Мой таб",
    val artist: String = "",
    val tempo: Int = 90,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    /** Строй, MIDI от 1-й (тонкой) струны к 6-й. */
    val tuning: List<Int> = STANDARD,
    val beats: List<EdBeat> = listOf(EdBeat()),
) {
    val barTicks: Int get() = beatsPerBar * 3840 / beatUnit

    /** Разбивка ударов на такты по размеру: удар, не влезающий в такт, переносится в следующий. */
    fun bars(): List<List<Int>> {
        val result = ArrayList<MutableList<Int>>()
        var current = ArrayList<Int>()
        var used = 0
        beats.forEachIndexed { i, b ->
            if (used > 0 && used + b.ticks > barTicks) {
                result += current
                current = ArrayList()
                used = 0
            }
            current += i
            used += b.ticks
            if (used >= barTicks) {
                result += current
                current = ArrayList()
                used = 0
            }
        }
        if (current.isNotEmpty() || result.isEmpty()) result += current
        return result
    }

    /** Текст в формате alphaTex — его рисует и проигрывает alphaTab, из него делается файл Guitar Pro. */
    fun toAlphaTex(): String = buildString {
        appendLine("\\title \"${esc(title)}\"")
        if (artist.isNotBlank()) appendLine("\\artist \"${esc(artist)}\"")
        appendLine("\\tempo $tempo")
        appendLine(".")
        appendLine("\\track \"Гитара\"")
        appendLine("\\instrument 25")
        appendLine("\\tuning " + tuning.joinToString(" ") { noteName(it) })
        appendLine(".")
        append("\\ts $beatsPerBar $beatUnit ")
        val bars = bars().filter { it.isNotEmpty() }
        bars.forEachIndexed { bi, bar ->
            append(bar.joinToString(" ") { beatTex(beats[it]) })
            append(if (bi < bars.lastIndex) " |\n" else " |")
        }
        appendLine()
    }

    private fun beatTex(b: EdBeat): String {
        val dur = ".${b.duration}" + if (b.dotted) "{d}" else ""
        if (b.isRest) return "r$dur"
        val notes = b.notes.joinToString(" ") { n ->
            val fret = if (n.effect == NoteEffect.DEAD) "x" else n.fret.toString()
            val eff = n.effect.tex.takeIf { it.isNotEmpty() }?.let { "{$it}" } ?: ""
            "$fret.${n.string}$eff"
        }
        return "($notes)$dur"
    }

    fun toJson(): String = JSONObject()
        .put("title", title).put("artist", artist).put("tempo", tempo)
        .put("ts", beatsPerBar).put("unit", beatUnit)
        .put("tuning", JSONArray(tuning))
        .put("beats", JSONArray().also { arr ->
            beats.forEach { b ->
                arr.put(JSONObject().put("d", b.duration).put("dot", b.dotted).put("n", JSONArray().also { na ->
                    b.notes.forEach { n -> na.put(JSONObject().put("s", n.string).put("f", n.fret).put("e", n.effect.name)) }
                }))
            }
        })
        .toString()

    companion object {
        /** Стандартный строй: e4 b3 g3 d3 a2 e2. */
        val STANDARD = listOf(64, 59, 55, 50, 45, 40)

        fun fromJson(json: String): EdScore {
            val o = JSONObject(json)
            val t = o.optJSONArray("tuning")
            val bs = o.getJSONArray("beats")
            return EdScore(
                title = o.optString("title", "Мой таб"),
                artist = o.optString("artist"),
                tempo = o.optInt("tempo", 90),
                beatsPerBar = o.optInt("ts", 4),
                beatUnit = o.optInt("unit", 4),
                tuning = if (t != null && t.length() > 0) (0 until t.length()).map { t.getInt(it) } else STANDARD,
                beats = (0 until bs.length()).map { i ->
                    val b = bs.getJSONObject(i)
                    val ns = b.getJSONArray("n")
                    EdBeat(
                        duration = b.optInt("d", 4),
                        dotted = b.optBoolean("dot"),
                        notes = (0 until ns.length()).map { j ->
                            val n = ns.getJSONObject(j)
                            EdNote(
                                n.getInt("s"), n.getInt("f"),
                                runCatching { NoteEffect.valueOf(n.optString("e")) }.getOrDefault(NoteEffect.NONE),
                            )
                        },
                    )
                }.ifEmpty { listOf(EdBeat()) },
            )
        }

        private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        fun noteName(midi: Int) = names[midi % 12] + (midi / 12 - 1)

        private fun esc(s: String) = s.replace("\\", "").replace("\"", "'")
    }
}
