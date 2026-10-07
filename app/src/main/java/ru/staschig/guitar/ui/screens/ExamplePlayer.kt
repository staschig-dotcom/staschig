package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.lessons.Chord
import ru.staschig.guitar.play.ExampleSynth
import ru.staschig.guitar.play.PlayScore

/**
 * Кнопка «Послушать пример»: звук готовится в фоне ([render]), повторное нажатие — стоп.
 */
@Composable
fun ExampleButton(
    label: String = "🔊 Послушать, как должно звучать",
    modifier: Modifier = Modifier.fillMaxWidth(),
    render: suspend () -> FloatArray?,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(0) } // 0 — ожидание, 1 — готовлю, 2 — играет
    DisposableEffect(Unit) { onDispose { if (state == 2) TonePlayer.stop() } }
    OutlinedButton(
        modifier = modifier,
        onClick = {
            if (state == 2) {
                TonePlayer.stop()
                state = 0
                return@OutlinedButton
            }
            if (state == 1) return@OutlinedButton
            state = 1
            scope.launch {
                val audio = withContext(Dispatchers.Default) { render() }
                if (audio == null || audio.isEmpty()) {
                    state = 0
                    return@launch
                }
                TonePlayer.playSamples(audio)
                state = 2
                delay(100)
                while (TonePlayer.isPlaying) delay(100)
                state = 0
            }
        },
    ) {
        Text(
            when (state) {
                1 -> "Готовлю звук…"
                2 -> "■ Остановить пример"
                else -> label
            }
        )
    }
}

/** Пример по табу (alphaTex / Guitar Pro): ноты достаются из таба, звучат синтезированной гитарой. */
@Composable
fun TabExampleButton(query: String, speed: Double = 1.0, label: String = "🔊 Послушать, как должно звучать") {
    var score by remember(query) { mutableStateOf<PlayScore?>(null) }
    var requested by remember(query) { mutableStateOf(false) }
    if (requested && score == null) {
        ScoreExtractor(query) { json -> score = runCatching { PlayScore.parse(json) }.getOrNull() }
    }
    ExampleButton(label) {
        requested = true
        // Ждём, пока скрытый WebView разберёт таб (обычно доли секунды).
        var waited = 0
        while (score == null && waited < 8000) {
            delay(50); waited += 50
        }
        score?.let { ExampleSynth.renderNotes(it.events, speed) }
    }
}

/** Пример для песни без таба: аккорды по порядку, по такту на аккорд, в темпе песни. */
@Composable
fun ChordsExampleButton(chords: List<Chord>, bpm: Int, pattern: String = "D.DUD.DU") {
    ExampleButton("🔊 Послушать аккорды песни") {
        ExampleSynth.renderChords(chords + chords, bpm, pattern = pattern)
    }
}
