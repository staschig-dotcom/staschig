package ru.staschig.guitar.audio

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

data class Tuning(
    val id: String,
    val title: String,
    val group: String,
    /** MIDI-ноты струн от 6-й (толстой) к 1-й. */
    val midi: List<Int>,
)

object Tunings {
    private val standard = listOf(40, 45, 50, 55, 59, 64)

    val all: List<Tuning> = listOf(
        Tuning("standard", "Стандартный (E A D G B E)", "Стандартный", standard),
        Tuning("half_down", "На полтона ниже (Eb)", "Пониженный", standard.map { it - 1 }),
        Tuning("full_down", "На тон ниже (D standard)", "Пониженный", standard.map { it - 2 }),
        Tuning("drop_d", "Drop D (D A D G B E)", "Пониженный", listOf(38, 45, 50, 55, 59, 64)),
        Tuning("drop_c", "Drop C (C G C F A D)", "Пониженный", listOf(36, 43, 48, 53, 57, 62)),
        Tuning("c_standard", "На 2 тона ниже (C standard)", "Пониженный", standard.map { it - 4 }),
        Tuning("half_up", "На полтона выше (F)", "Повышенный", standard.map { it + 1 }),
        Tuning("full_up", "На тон выше (F#)", "Повышенный", standard.map { it + 2 }),
        Tuning("open_g", "Open G (D G D G B D)", "Открытый", listOf(38, 43, 50, 55, 59, 62)),
        Tuning("open_d", "Open D (D A D F# A D)", "Открытый", listOf(38, 45, 50, 54, 57, 62)),
        Tuning("dadgad", "DADGAD", "Открытый", listOf(38, 45, 50, 55, 57, 62)),
    )

    fun byId(id: String): Tuning = all.firstOrNull { it.id == id } ?: all.first()
}

object Notes {
    private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun name(midi: Int): String = names[((midi % 12) + 12) % 12]
    fun nameWithOctave(midi: Int): String = name(midi) + (midi / 12 - 1)

    fun frequency(midi: Int, a4: Float = 440f): Float = (a4 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    /** Дробный номер MIDI для частоты. */
    fun midiOf(freq: Float, a4: Float = 440f): Float = (69 + 12 * log2(freq / a4.toDouble())).toFloat()

    fun nearestMidi(freq: Float, a4: Float = 440f): Int = midiOf(freq, a4).roundToInt()

    /** Отклонение в центах от целевой ноты. */
    fun cents(freq: Float, targetMidi: Int, a4: Float = 440f): Float = (midiOf(freq, a4) - targetMidi) * 100f
}
