package ru.staschig.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Щелчок для ритм-теста: тон 4 кГц с окном Ханна 12 мс. Плавная огибающая не даёт
 * спектру «расползтись» вниз, поэтому фильтр гитарной полосы щелчок не слышит.
 */
object RhythmClick {
    private val cache = HashMap<Int, FloatArray>()

    fun samples(sampleRate: Int): FloatArray = cache.getOrPut(sampleRate) {
        val n = (sampleRate * 0.012).toInt()
        FloatArray(n) { i ->
            val w = 0.5 - 0.5 * cos(2 * PI * i / (n - 1))
            (0.9 * w * sin(2 * PI * 4000.0 * i / sampleRate)).toFloat()
        }
    }
}
