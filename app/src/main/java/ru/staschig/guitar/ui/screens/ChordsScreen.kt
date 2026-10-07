package ru.staschig.guitar.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.staschig.guitar.audio.ChordRecognizer
import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.audio.MicStream
import ru.staschig.guitar.audio.PitchDetector
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.Chords
import ru.staschig.guitar.ui.theme.InTune

private const val WINDOW = 8192

/**
 * Микрофон → распознавание аккорда. [onResult] вызывается из фонового потока
 * с отсортированными совпадениями (или null, если тихо).
 */
private class ChordListener(onResult: (List<ChordRecognizer.Match>?) -> Unit) {
    private val recognizer = ChordRecognizer(44100, Chords.recognizable)
    private val ring = FloatArray(WINDOW)
    private val window = FloatArray(WINDOW)
    private var filled = 0
    private val mic = MicStream(block = 2048, source = android.media.MediaRecorder.AudioSource.MIC) { samples, _ ->
        System.arraycopy(ring, samples.size, ring, 0, WINDOW - samples.size)
        System.arraycopy(samples, 0, ring, WINDOW - samples.size, samples.size)
        filled = minOf(WINDOW, filled + samples.size)
        if (filled == WINDOW) {
            System.arraycopy(ring, 0, window, 0, WINDOW)
            onResult(if (PitchDetector.rms(window) < 0.008f) null else recognizer.match(window))
        }
    }

    fun start() = mic.start()
    fun stop() = mic.stop()
}

/** «Одноминутные смены» (Justin Guitar) с автоматическим подсчётом смен по микрофону. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChordsScreen(store: AppStore, onBack: () -> Unit) {
    val (granted, requestMic) = rememberMicPermission()
    var pair by remember { mutableStateOf(Chords.changePairs.first()) }
    var heard by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var changes by remember { mutableIntStateOf(0) }
    val a = Chords.byName(pair.first)!!
    val b = Chords.byName(pair.second)!!
    val key = "changes_${pair.first}_${pair.second}"
    val best = store.bpmRecords[key]
    val currentPair by rememberUpdatedState(pair)
    val isRunning by rememberUpdatedState(running)

    // Состояние распознавания смен (живёт в фоновом потоке микрофона).
    val tracker = remember {
        object {
            var candidate: String? = null
            var streak = 0
            var confirmed: String? = null
        }
    }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val listener = ChordListener { matches ->
            if (matches == null) { heard = null; return@ChordListener }
            val top = matches.first()
            heard = if (top.score > 0.6f) top.chord.name else null
            // Для смен важно только «какой из двух аккордов пары ближе».
            val (pa, pb) = currentPair
            val sa = matches.firstOrNull { it.chord.name == pa }?.score ?: 0f
            val sb = matches.firstOrNull { it.chord.name == pb }?.score ?: 0f
            val bestOfPair = if (sa >= sb) pa else pb
            val ok = maxOf(sa, sb) >= top.score - 0.05f && maxOf(sa, sb) > 0.6f
            val cand = if (ok) bestOfPair else null
            if (cand == tracker.candidate) tracker.streak++ else { tracker.candidate = cand; tracker.streak = 1 }
            if (cand != null && tracker.streak == 2 && cand != tracker.confirmed) {
                if (tracker.confirmed != null && isRunning) changes++
                tracker.confirmed = cand
            }
        }
        listener.start()
        onDispose { listener.stop() }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        secondsLeft = 60
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        running = false
        store.recordBpm(key, changes)
        store.addPracticeMinutes(1)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Смены аккордов") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Метод Justin Guitar: за минуту меняйте два аккорда как можно больше раз. Приложение считает " +
                    "смены по звуку; если пропустило — нажмите «+1». Записывайте рекорд и повторяйте каждый день.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chords.changePairs.forEach { p ->
                    FilterChip(pair == p, { if (!running) { pair = p; changes = 0 } }, { Text("${p.first}↔${p.second}") })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ChordDiagram(a)
                ChordDiagram(b)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { TonePlayer.playSamples(GuitarSynth.render(a.midi)) }) { Text("🔊 ${a.name}") }
                OutlinedButton(onClick = { TonePlayer.playSamples(GuitarSynth.render(b.midi)) }) { Text("🔊 ${b.name}") }
            }

            if (!granted) {
                Button(onClick = requestMic) { Text("Разрешить микрофон для автоподсчёта") }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Слышу:", Modifier.padding(end = 12.dp))
                        Text(
                            heard ?: "—",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (heard == a.name || heard == b.name) InTune else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$changes", fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text(if (running) "смен · осталось $secondsLeft с" else "смен" + (best?.let { " · рекорд $it" } ?: ""))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (running) running = false
                        else { changes = 0; tracker.confirmed = null; running = true }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (running) "Стоп" else "Старт: 1 минута") }
                OutlinedButton(onClick = { changes++ }, modifier = Modifier.height(52.dp)) { Text("+1") }
            }
            Text(
                "Распознавание по микрофону приблизительное: лучше всего работает в тишине и при чётком ударе по всем струнам.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
