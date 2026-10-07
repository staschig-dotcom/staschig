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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.lessons.Chord
import ru.staschig.guitar.lessons.Chords
import ru.staschig.guitar.ui.theme.InTune
import ru.staschig.guitar.ui.theme.OutOfTune

private data class EarDrill(val title: String, val hint: String, val items: List<Pair<String, String>>)

/** Упражнения на слух: (аккорд, правильный ответ). */
private val drills = listOf(
    EarDrill(
        "Мажор / минор", "Мажор звучит светло, минор — грустно.",
        listOf("A", "Am", "D", "Dm", "E", "Em", "C", "Cm", "G", "Gm").map { it to if (it.endsWith("m")) "Минор" else "Мажор" },
    ),
    EarDrill(
        "Открытые аккорды", "Узнайте аккорд целиком — как при подборе песни на слух.",
        listOf("A", "Am", "C", "D", "Dm", "E", "Em", "G").map { it to it },
    ),
    EarDrill(
        "Септаккорды", "7 — напряжённый «блюзовый», maj7 — мягкий «джазовый», m7 — мягкий минорный.",
        listOf(
            "A7" to "7", "D7" to "7", "E7" to "7", "G7" to "7", "B7" to "7", "C7" to "7",
            "Cmaj7" to "maj7", "Fmaj7" to "maj7", "Am7" to "m7", "Dm7" to "m7", "Em7" to "m7",
            "A" to "Мажор", "Am" to "Минор",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EarScreen(onBack: () -> Unit) {
    var drill by remember { mutableStateOf(drills.first()) }
    var current by remember { mutableStateOf(drills.first().items.random()) }
    var answered by remember { mutableStateOf<String?>(null) }
    var correct by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var arpeggio by remember { mutableStateOf(false) }
    val options = drill.items.map { it.second }.distinct()
    val chord: Chord = Chords.byName(current.first)!!

    fun play(name: String = current.first) = TonePlayer.playSamples(
        GuitarSynth.render(Chords.byName(name)!!.midi, strumMs = if (arpeggio) 220f else 18f, seconds = if (arpeggio) 3.2f else 2.5f)
    )

    fun next() {
        var n = drill.items.random()
        while (drill.items.size > 1 && n == current) n = drill.items.random()
        current = n
        answered = null
        play(n.first)
    }

    DisposableEffect(Unit) { onDispose { TonePlayer.stop() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Слух: аккорды") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                drills.forEach { d ->
                    FilterChip(drill == d, {
                        drill = d; current = d.items.random(); answered = null; correct = 0; total = 0
                    }, { Text(d.title) })
                }
            }
            Text(drill.hint, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { play() }, modifier = Modifier.height(52.dp)) { Text("🔊 Сыграть") }
                FilterChip(arpeggio, { arpeggio = !arpeggio }, { Text("Перебором") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { opt ->
                    val colors = when {
                        answered == null -> ButtonDefaults.outlinedButtonColors()
                        opt == current.second -> ButtonDefaults.outlinedButtonColors(containerColor = InTune.copy(alpha = 0.3f))
                        opt == answered -> ButtonDefaults.outlinedButtonColors(containerColor = OutOfTune.copy(alpha = 0.3f))
                        else -> ButtonDefaults.outlinedButtonColors()
                    }
                    OutlinedButton(
                        onClick = {
                            if (answered == null) {
                                answered = opt
                                total++
                                if (opt == current.second) correct++
                            }
                        },
                        colors = colors,
                    ) { Text(opt) }
                }
            }
            if (answered != null) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (answered == current.second) "Верно! Это ${chord.name}" else "Это был ${chord.name} (${current.second})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ChordDiagram(chord)
                        Button(onClick = { next() }) { Text("Следующий") }
                    }
                }
            }
            Text("Счёт: $correct из $total", style = MaterialTheme.typography.titleSmall)
        }
    }
}
