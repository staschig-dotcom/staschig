package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.staschig.guitar.audio.MetronomeEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen() {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Метроном") })
        MetronomePanel(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp))
    }
}

/**
 * Метроном без своего экрана — встраивается и в отдельный экран, и в шаг урока.
 * Состояние читается из [MetronomeEngine], поэтому панель видит, когда метроном
 * запускает таймер урока или тренажёр скорости меняет темп.
 * [onRecord] — кнопка «Сыграл чисто» (запомнить текущий темп как рекорд).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomePanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onRecord: ((Int) -> Unit)? = null,
) {
    val m = MetronomeEngine
    var bpm by remember { mutableIntStateOf(m.bpm) }
    var running by remember { mutableStateOf(m.isRunning) }
    var beats by remember { mutableIntStateOf(m.beatsPerBar) }
    var sub by remember { mutableIntStateOf(m.subdivision) }
    var accent by remember { mutableStateOf(m.accentFirst) }
    var trainer by remember { mutableStateOf(m.trainerEnabled) }
    var trainerTarget by remember { mutableIntStateOf(m.trainerTarget) }
    var beat by remember { mutableIntStateOf(-1) }
    val taps = remember { mutableStateListOf<Long>() }

    // Синхронизация с движком: индикатор долей по реальному звуку, темп, старт/стоп извне.
    LaunchedEffect(Unit) {
        while (true) {
            running = m.isRunning
            trainer = m.trainerEnabled
            trainerTarget = m.trainerTarget
            if (running) {
                val (b, currentBpm) = m.currentBeat()
                beat = b
                bpm = currentBpm
            } else {
                beat = -1
                bpm = m.bpm
            }
            delay(16)
        }
    }

    fun setBpm(v: Int) {
        m.bpm = v
        bpm = m.bpm
    }

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(beats) { i ->
                val active = i == beat
                Box(
                    Modifier
                        .size(if (i == 0) 28.dp else 22.dp)
                        .background(
                            when {
                                active && i == 0 && accent -> MaterialTheme.colorScheme.primary
                                active -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape,
                        )
                )
            }
        }
        Text("$bpm", fontSize = if (compact) 52.sp else 80.sp, fontWeight = FontWeight.Bold)
        Text("BPM · ${tempoName(bpm)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = bpm.toFloat(),
            onValueChange = { setBpm(it.toInt()) },
            valueRange = 30f..250f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-5, -1, +1, +5).forEach { d ->
                OutlinedButton(onClick = { setBpm(bpm + d) }) { Text(if (d > 0) "+$d" else "$d") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { m.toggle(); running = m.isRunning }, modifier = Modifier.size(width = 160.dp, height = 56.dp)) {
                Text(if (running) "Стоп" else "Старт", fontSize = 20.sp)
            }
            OutlinedButton(onClick = {
                val now = System.currentTimeMillis()
                if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                taps.add(now)
                if (taps.size > 6) taps.removeAt(0)
                if (taps.size >= 2) {
                    val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
                    setBpm((60000 / avg).toInt())
                }
            }, modifier = Modifier.size(width = 120.dp, height = 56.dp)) { Text("Tap") }
        }

        if (onRecord != null) {
            OutlinedButton(onClick = { onRecord(bpm) }) { Text("✓ Сыграл чисто на $bpm BPM") }
        }
        if (!compact) {
            Text("Размер", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 4, 5, 6, 7).forEach { b ->
                    FilterChip(selected = beats == b, onClick = { beats = b; m.beatsPerBar = b }, label = { Text("$b/4") })
                }
            }
        }
        Text("Дробление доли", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "♩", 2 to "♪♪", 3 to "триоли", 4 to "16-е").forEach { (v, label) ->
                FilterChip(selected = sub == v, onClick = { sub = v; m.subdivision = v }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Акцент на первую долю", Modifier.weight(1f))
            Switch(checked = accent, onCheckedChange = { accent = it; m.accentFirst = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Тренажёр скорости")
                Text(
                    "+${m.trainerStep} BPM каждые ${m.trainerBars} такта до $trainerTarget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = trainer, onCheckedChange = { trainer = it; m.trainerEnabled = it })
        }
        if (trainer) {
            Slider(
                value = trainerTarget.toFloat(),
                onValueChange = { trainerTarget = it.toInt(); m.trainerTarget = it.toInt() },
                valueRange = 40f..250f,
            )
        }
    }
}

private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante / Moderato"
    bpm < 120 -> "Allegretto"
    bpm < 168 -> "Allegro"
    else -> "Presto"
}
