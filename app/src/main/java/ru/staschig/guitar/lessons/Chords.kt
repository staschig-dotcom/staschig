package ru.staschig.guitar.lessons

/**
 * Аккорды с аппликатурами. frets — от 6-й струны к 1-й, null = струна не играется.
 */
data class Chord(val name: String, val frets: List<Int?>) {
    /** MIDI-ноты звучащих струн в стандартном строе. */
    val midi: List<Int> get() = frets.mapIndexedNotNull { i, f -> f?.let { STANDARD[i] + it } }

    val shape: String get() = frets.joinToString("") { it?.toString() ?: "x" }

    companion object {
        val STANDARD = listOf(40, 45, 50, 55, 59, 64)

        /** "x32010" или "x,13,12,..." для ладов > 9. */
        fun parse(name: String, shape: String): Chord {
            val parts = if (shape.contains(',')) shape.split(',') else shape.map { it.toString() }
            require(parts.size == 6) { "bad shape $shape" }
            return Chord(name, parts.map { it.trim().toIntOrNull() })
        }
    }
}

object Chords {
    val all: List<Chord> = listOf(
        "A" to "x02220", "Am" to "x02210", "A7" to "x02020", "Am7" to "x02010",
        "Asus2" to "x02200", "Asus4" to "x02230", "A7sus4" to "x02033",
        "B7" to "x21202", "Bm" to "x24432", "Bb" to "x13331",
        "C" to "x32010", "Cmaj7" to "x32000", "Cadd9" to "x32030", "C7" to "x32310", "Cm" to "x35543",
        "C/G" to "332010",
        "D" to "xx0232", "Dm" to "xx0231", "D7" to "xx0212", "Dm7" to "xx0211",
        "Dsus2" to "xx0230", "Dsus4" to "xx0233", "D/F#" to "200232", "D6/9" to "2x4220",
        "E" to "022100", "Em" to "022000", "E7" to "020100", "Em7" to "022033",
        "F" to "133211", "Fmaj7" to "xx3210", "F#m" to "244222", "F#" to "244322",
        "G" to "320003", "G7" to "320001", "G/B" to "x20003", "Gm" to "355333",
        "E5" to "022xxx", "A5" to "x022xx", "D5" to "xx023x", "G5" to "355xxx",
        "F5" to "133xxx", "Bb5" to "x133xx", "Ab5" to "466xxx", "Db5" to "x466xx",
    ).map { (n, s) -> Chord.parse(n, s) }

    private val byName = all.associateBy { it.name }

    fun byName(name: String): Chord? = byName[name]

    /** Пары для «одноминутных смен» по уровням (Justin Guitar). */
    val changePairs: List<Pair<String, String>> = listOf(
        "A" to "D", "D" to "E", "A" to "E", "Am" to "E", "Am" to "C", "Em" to "G",
        "C" to "G", "G" to "D", "Em" to "C", "C" to "Fmaj7", "C" to "F", "G" to "Em",
        "Am" to "F", "D" to "Bm", "F" to "G",
    )

    /** Аккорды, среди которых распознавание выбирает «что звучит». */
    val recognizable: List<Chord> = all.filter { it.midi.size >= 4 && it.name !in setOf("C/G", "G/B", "D/F#", "D6/9") }
}
