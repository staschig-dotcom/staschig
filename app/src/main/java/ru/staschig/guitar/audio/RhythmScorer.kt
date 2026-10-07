package ru.staschig.guitar.audio

import kotlin.math.abs
import kotlin.math.sqrt

/** Оценка точности игры относительно щелчков метронома. Все времена — в миллисекундах. */
object RhythmScorer {
    data class Hit(val tickMs: Double, val deviationMs: Double?)

    data class Result(
        val hits: List<Hit>,
        val meanMs: Double,
        val stdMs: Double,
        val accuracy: Int,
        val missed: Int,
        val extra: Int,
    ) {
        val verdict: String
            get() = when {
                hits.none { it.deviationMs != null } -> "Удары не услышаны — играйте громче или ближе к телефону"
                meanMs > 15 -> "Тянете: в среднем на ${meanMs.toInt()} мс позже щелчка"
                meanMs < -15 -> "Спешите: в среднем на ${(-meanMs).toInt()} мс раньше щелчка"
                stdMs > 30 -> "В среднем в долю, но неровно (разброс ±${stdMs.toInt()} мс)"
                else -> "Отлично: ровно и в долю"
            }
    }

    /** Порог «точного» попадания, мс. */
    const val TOLERANCE_MS = 35.0

    /**
     * @param ticks моменты щелчков, которые нужно было сыграть
     * @param onsets моменты обнаруженных ударов (уже без задержки системы)
     */
    fun score(ticks: List<Double>, onsets: List<Double>): Result {
        if (ticks.isEmpty()) return Result(emptyList(), 0.0, 0.0, 0, 0, onsets.size)
        val interval = if (ticks.size > 1) (ticks.last() - ticks.first()) / (ticks.size - 1) else 500.0
        val window = interval / 2
        val used = BooleanArray(onsets.size)
        val hits = ticks.map { t ->
            var best = -1
            var bestDist = Double.MAX_VALUE
            onsets.forEachIndexed { i, o ->
                val d = abs(o - t)
                if (!used[i] && d < window && d < bestDist) {
                    best = i; bestDist = d
                }
            }
            if (best >= 0) {
                used[best] = true
                Hit(t, onsets[best] - t)
            } else Hit(t, null)
        }
        val devs = hits.mapNotNull { it.deviationMs }
        val mean = if (devs.isEmpty()) 0.0 else devs.average()
        val std = if (devs.size < 2) 0.0 else sqrt(devs.sumOf { (it - mean) * (it - mean) } / (devs.size - 1))
        val accurate = devs.count { abs(it) <= TOLERANCE_MS }
        // Лишние удары считаем только внутри проверяемого отрезка.
        val from = ticks.first() - window
        val to = ticks.last() + window
        val extra = onsets.indices.count { !used[it] && onsets[it] in from..to }
        return Result(
            hits = hits,
            meanMs = mean,
            stdMs = std,
            accuracy = (100 * accurate / ticks.size),
            missed = hits.count { it.deviationMs == null },
            extra = extra,
        )
    }

    /**
     * Остаточная задержка «динамик → микрофон» по калибровке: медиана (услышали − сыграли).
     * Окно −100..400 мс: метки времени Android уже учитывают часть задержки, остаток бывает и отрицательным.
     */
    fun latency(clicks: List<Double>, heard: List<Double>): Double? {
        val diffs = clicks.mapNotNull { c ->
            heard.filter { it - c in -100.0..400.0 }.minByOrNull { abs(it - c) }?.minus(c)
        }
        if (diffs.size < clicks.size / 2 || diffs.size < 4) return null
        return diffs.sorted()[diffs.size / 2]
    }
}
