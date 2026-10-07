package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.staschig.guitar.audio.MetronomeEngine
import ru.staschig.guitar.audio.MicStream
import ru.staschig.guitar.audio.OnsetDetector
import ru.staschig.guitar.audio.RhythmScorer
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.RhythmRun
import ru.staschig.guitar.ui.theme.InTune
import ru.staschig.guitar.ui.theme.OutOfTune
import java.time.LocalDate
import java.util.Collections
import kotlin.math.abs

private enum class Phase { IDLE, CALIBRATING, COUNT_IN, PLAYING }

/** Запись: метроном играет щелчки, микрофон ловит удары. Возвращает (щелчки, удары) в нс. */
private suspend fun record(
    band: OnsetDetector.Band,
    bpm: Int,
    subdivision: Int,
    bars: Int,
    onPhase: (Phase) -> Unit,
    playingPhase: Phase,
): Pair<List<Long>, List<Long>>? {
    val onsets = Collections.synchronizedList(ArrayList<Long>())
    val detector = OnsetDetector(44100, band)
    var blockStart = 0L
    val mic = MicStream { samples, startNanos ->
        detector.process(samples).forEach { idx ->
            onsets.add(startNanos + (idx - blockStart) * 1_000_000_000L / 44100)
        }
        blockStart += samples.size
    }
    if (!mic.start()) return null
    val m = MetronomeEngine
    m.stop()
    m.bpm = bpm
    m.beatsPerBar = 4
    m.subdivision = subdivision
    m.accentFirst = true
    m.trainerEnabled = false
    m.rhythmClick = true
    try {
        delay(300)
        m.start()
        val barMs = 4 * 60_000L / bpm
        if (playingPhase == Phase.PLAYING) {
            onPhase(Phase.COUNT_IN)
            delay(barMs)
        }
        onPhase(playingPhase)
        delay(barMs * bars + 400)
        return m.clickTimesNanos() to synchronized(onsets) { onsets.toList() }
    } finally {
        m.stop()
        m.rhythmClick = false
        mic.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RhythmScreen(store: AppStore, onBack: () -> Unit) {
    val (granted, requestMic) = rememberMicPermission()
    val scope = rememberCoroutineScope()
    var bpm by remember { mutableIntStateOf(80) }
    var subdivision by remember { mutableIntStateOf(1) }
    var bars by remember { mutableIntStateOf(4) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var result by remember { mutableStateOf<RhythmScorer.Result?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val latency = store.settings.latencyMs

    DisposableEffect(Unit) { onDispose { MetronomeEngine.stop(); MetronomeEngine.rhythmClick = false } }

    fun calibrate() = scope.launch {
        message = null
        val data = record(OnsetDetector.Band.HIGH, 100, 1, 3, { phase = it }, Phase.CALIBRATING)
        phase = Phase.IDLE
        if (data == null) { message = "Микрофон недоступен"; return@launch }
        val (clicks, heard) = data
        val t0 = clicks.firstOrNull() ?: return@launch
        val lat = RhythmScorer.latency(clicks.map { (it - t0) / 1e6 }, heard.map { (it - t0) / 1e6 })
        if (lat == null) {
            message = "Не удалось услышать щелчки. Прибавьте громкость, отключите наушники и повторите в тишине."
        } else {
            store.updateSettings { it.copy(latencyMs = lat.toInt()) }
            message = "Готово: поправка ${lat.toInt()} мс"
        }
    }

    fun test() = scope.launch {
        message = null
        result = null
        val data = record(OnsetDetector.Band.LOW, bpm, subdivision, bars, { phase = it }, Phase.PLAYING)
        phase = Phase.IDLE
        if (data == null) { message = "Микрофон недоступен"; return@launch }
        val (clicks, onsets) = data
        val perBar = 4 * subdivision
        val scored = clicks.drop(perBar).take(bars * perBar) // первый такт — отсчёт
        if (scored.isEmpty()) { message = "Метроном не успел прозвучать — попробуйте ещё раз"; return@launch }
        val t0 = scored.first()
        val lat = (latency ?: 0) * 1_000_000L
        val r = RhythmScorer.score(scored.map { (it - t0) / 1e6 }, onsets.map { (it - lat - t0) / 1e6 })
        result = r
        if (r.hits.any { it.deviationMs != null }) {
            store.addRhythmRun(RhythmRun(LocalDate.now().toString(), bpm, r.accuracy, r.meanMs.toInt(), r.stdMs.toInt()))
            store.addPracticeMinutes(1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Ритм-тест") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Играйте приглушёнными ударами вниз точно в щелчок. Первый такт — отсчёт. " +
                    "Телефон положите рядом с гитарой, звук — через динамик (не Bluetooth-наушники).",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!granted) {
                Button(onClick = requestMic) { Text("Разрешить микрофон") }
                return@Column
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Калибровка задержки", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (latency == null) "Не выполнена — результат может быть смещён. Займёт 3 секунды, гитара не нужна, нужна тишина."
                        else "Поправка: $latency мс. Повторите, если сменили телефон или громкость.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(enabled = phase == Phase.IDLE, onClick = { calibrate() }) {
                        Text(if (phase == Phase.CALIBRATING) "Слушаю щелчки…" else "Откалибровать")
                    }
                }
            }

            Text("Темп: $bpm BPM", style = MaterialTheme.typography.titleSmall)
            Slider(bpm.toFloat(), { bpm = it.toInt() }, valueRange = 50f..160f, enabled = phase == Phase.IDLE)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "Четверти", 2 to "Восьмые").forEach { (v, l) ->
                    FilterChip(subdivision == v, { subdivision = v }, { Text(l) }, enabled = phase == Phase.IDLE)
                }
                listOf(4, 8).forEach { b ->
                    FilterChip(bars == b, { bars = b }, { Text("$b такта") }, enabled = phase == Phase.IDLE)
                }
            }
            Button(
                enabled = phase == Phase.IDLE,
                onClick = { test() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    when (phase) {
                        Phase.COUNT_IN -> "Отсчёт… 1 2 3 4"
                        Phase.PLAYING -> "Играйте!"
                        Phase.CALIBRATING -> "Калибровка…"
                        Phase.IDLE -> "Начать тест"
                    }
                )
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

            result?.let { r -> ResultCard(r) }

            if (store.rhythmRuns.isNotEmpty()) {
                Text("История", style = MaterialTheme.typography.titleSmall)
                store.rhythmRuns.takeLast(8).reversed().forEach {
                    Text(
                        "${it.date} · ${it.bpm} BPM · точность ${it.accuracy}% · смещение ${it.meanMs} мс · разброс ±${it.stdMs} мс",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(r: RhythmScorer.Result) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.verdict, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Точно: ${r.accuracy}%")
                Text("Смещение: ${r.meanMs.toInt()} мс")
                Text("Разброс: ±${r.stdMs.toInt()}")
            }
            Text("Пропущено: ${r.missed} · лишних ударов: ${r.extra}", style = MaterialTheme.typography.bodySmall)
            val ok = InTune
            val bad = OutOfTune
            val band = MaterialTheme.colorScheme.surfaceVariant
            val axis = MaterialTheme.colorScheme.onSurfaceVariant
            // По горизонтали — доли, по вертикали — отклонение (вверх = поздно), полоса = ±35 мс.
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val range = 100f
                fun y(ms: Float) = size.height / 2 - (ms.coerceIn(-range, range) / range) * (size.height / 2 - 6f)
                val tol = RhythmScorer.TOLERANCE_MS.toFloat()
                drawRect(band, Offset(0f, y(tol)), Size(size.width, y(-tol) - y(tol)))
                drawLine(axis, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
                val step = size.width / (r.hits.size + 1)
                r.hits.forEachIndexed { i, h ->
                    val x = step * (i + 1)
                    val d = h.deviationMs
                    if (d == null) {
                        drawLine(bad, Offset(x - 6, 6f), Offset(x + 6, 18f), 3f)
                        drawLine(bad, Offset(x + 6, 6f), Offset(x - 6, 18f), 3f)
                    } else {
                        drawCircle(if (abs(d) <= tol) ok else bad, 7f, Offset(x, y(d.toFloat())))
                    }
                }
            }
            Text("Вверх — играете позже щелчка, вниз — раньше. ✕ — удар не услышан.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
