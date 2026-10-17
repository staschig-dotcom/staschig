package ru.staschig.guitar.lessons

/**
 * Аккорд с аппликатурой.
 * frets — от 6-й струны к 1-й, null = струна не играется.
 * fingers — каким пальцем прижимать (от 6-й к 1-й): 0 — не прижимается, 1 — указательный,
 * 2 — средний, 3 — безымянный, 4 — мизинец, 5 — большой палец (обхват грифа).
 */
data class Chord(val name: String, val frets: List<Int?>, val fingers: List<Int> = List(6) { 0 }) {
    /** MIDI-ноты звучащих струн в стандартном строе. */
    val midi: List<Int> get() = frets.mapIndexedNotNull { i, f -> f?.let { STANDARD[i] + it } }

    val shape: String get() = frets.joinToString("") { it?.toString() ?: "x" }

    /** Баррэ: указательный палец прижимает несколько струн на одном ладу. Возвращает (лад, от струны, до струны) — индексы 0..5. */
    val barre: Triple<Int, Int, Int>?
        get() {
            val idx = fingers.indices.filter { fingers[it] == 1 && (frets[it] ?: 0) > 0 }
            if (idx.size < 2) return null
            val fret = frets[idx.first()] ?: return null
            if (idx.any { frets[it] != fret }) return null
            return Triple(fret, idx.first(), idx.last())
        }

    companion object {
        val STANDARD = listOf(40, 45, 50, 55, 59, 64)

        /** "x32010" (или "x,13,12,..." для ладов > 9) + пальцы "032010" (T — большой палец). */
        fun parse(name: String, shape: String, fingers: String = "000000"): Chord {
            val parts = if (shape.contains(',')) shape.split(',') else shape.map { it.toString() }
            require(parts.size == 6) { "bad shape $shape" }
            require(fingers.length == 6) { "bad fingers $fingers" }
            return Chord(
                name,
                parts.map { it.trim().toIntOrNull() },
                fingers.map { if (it == 'T') 5 else it.digitToInt() },
            )
        }
    }
}

object Chords {
    /** (название, лады, пальцы) — общепринятые аппликатуры для начинающих. */
    val all: List<Chord> = listOf(
        Triple("A", "x02220", "001230"), Triple("Am", "x02210", "002310"), Triple("A7", "x02020", "001020"),
        Triple("Am7", "x02010", "002010"), Triple("Asus2", "x02200", "001200"), Triple("Asus4", "x02230", "001230"),
        Triple("A7sus4", "x02033", "001034"),
        Triple("B7", "x21202", "021304"), Triple("Bm", "x24432", "013421"), Triple("Bb", "x13331", "013331"),
        Triple("C", "x32010", "032010"), Triple("Cmaj7", "x32000", "032000"), Triple("Cadd9", "x32030", "021030"),
        Triple("C7", "x32310", "032410"), Triple("Cm", "x35543", "013421"),
        Triple("C/G", "332010", "432010"),
        Triple("D", "xx0232", "000132"), Triple("Dm", "xx0231", "000231"), Triple("D7", "xx0212", "000213"),
        Triple("Dm7", "xx0211", "000211"),
        Triple("Dsus2", "xx0230", "000130"), Triple("Dsus4", "xx0233", "000134"), Triple("D/F#", "200232", "T00132"),
        Triple("D6/9", "2x4220", "203110"),
        Triple("E", "022100", "023100"), Triple("Em", "022000", "023000"), Triple("E7", "020100", "020100"),
        Triple("Em7", "022033", "012034"),
        Triple("F", "133211", "134211"), Triple("Fmaj7", "xx3210", "003210"), Triple("F#m", "244222", "134111"),
        Triple("F#", "244322", "134211"),
        Triple("G", "320003", "210003"), Triple("G7", "320001", "320001"), Triple("G/B", "x20003", "010003"),
        Triple("Gm", "355333", "134111"),
        Triple("E5", "022xxx", "013000"), Triple("A5", "x022xx", "001300"), Triple("D5", "xx023x", "000130"),
        Triple("G5", "355xxx", "134000"),
        Triple("F5", "133xxx", "134000"), Triple("Bb5", "x133xx", "013400"), Triple("Ab5", "466xxx", "134000"),
        Triple("Db5", "x466xx", "013400"),
    ).map { (n, s, f) -> Chord.parse(n, s, f) }

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
