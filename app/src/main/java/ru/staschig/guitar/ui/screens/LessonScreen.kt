package ru.staschig.guitar.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.staschig.guitar.audio.MetronomeEngine
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Lesson
import ru.staschig.guitar.lessons.Step
import ru.staschig.guitar.lessons.StepKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    store: AppStore,
    lessonId: String,
    onBack: () -> Unit,
    onOpenUrl: (url: String, lessonId: String?) -> Unit,
) {
    val lesson = Curriculum.byId(lessonId)
    if (lesson == null) {
        Text("Урок не найден", Modifier.padding(16.dp))
        return
    }
    val steps = lesson.steps(store.settings.dailyMinutes, store.settings.focus)

    var current by rememberSaveable { mutableIntStateOf(0) }
    var remaining by rememberSaveable { mutableIntStateOf(steps[0].minutes * 60) }
    var running by rememberSaveable { mutableStateOf(false) }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    val latestElapsed by rememberUpdatedState(elapsed)

    val beeper = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            elapsed++
            if (remaining > 0) {
                remaining--
                if (remaining == 0) beeper?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            }
        }
    }

    // Время практики засчитывается, даже если выйти из урока, не завершив его.
    DisposableEffect(Unit) {
        onDispose {
            store.addPracticeMinutes((latestElapsed + 30) / 60)
            MetronomeEngine.stop()
            beeper?.release()
        }
    }

    fun goTo(index: Int) {
        current = index
        remaining = steps[index].minutes * 60
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Урок ${lesson.number} · ${lesson.level.title}") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(lesson.technique.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "План на ${steps.sumOf { it.minutes }} мин: " + steps.joinToString(" → ") { "${it.kind.title} ${it.minutes}′" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Таймер текущего блока
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(steps[current].kind.title, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "%d:%02d".format(remaining / 60, remaining % 60),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Всего занимаетесь: ${elapsed / 60} мин",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { running = !running }) {
                        Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Старт/пауза")
                    }
                    IconButton(onClick = { if (current < steps.lastIndex) goTo(current + 1) }) {
                        Icon(Icons.Filled.SkipNext, "Следующий блок")
                    }
                }
            }

            steps.forEachIndexed { index, step ->
                StepCard(
                    store = store,
                    lesson = lesson,
                    step = step,
                    active = index == current,
                    onSelect = { goTo(index) },
                    onOpenUrl = onOpenUrl,
                )
            }

            val done = lesson.id in store.completed
            Button(
                onClick = {
                    running = false
                    store.addPracticeMinutes((elapsed + 30) / 60)
                    elapsed = 0
                    store.completeLesson(lesson.id)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (done) "Пройти ещё раз и засчитать время" else "Завершить урок") }
            if (done) {
                TextButton(onClick = { store.uncompleteLesson(lesson.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Отметить как непройденный")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCard(
    store: AppStore,
    lesson: Lesson,
    step: Step,
    active: Boolean,
    onSelect: () -> Unit,
    onOpenUrl: (String, String?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${step.kind.title} · ${step.minutes} мин",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(step.title, style = MaterialTheme.typography.titleMedium)

            step.exercise?.let { ex ->
                Text("Школа: ${ex.school}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
                Text(ex.description)
                ex.tab?.let { TabText(it) }
                if (ex.bpmStart != null) {
                    BpmControls(store, ex.id, ex.bpmStart, ex.bpmTarget ?: ex.bpmStart)
                }
            }

            step.song?.let { song ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    song.chords.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
                Text("Ритм: ${song.strumming}", style = MaterialTheme.typography.bodyMedium)
                Text(song.description)
                song.tab?.let { TabText(it) }
                song.bpm?.let { BpmControls(store, song.id, (it * 0.7).toInt(), it) }

                Text("Табы для разбора", style = MaterialTheme.typography.titleSmall)
                val attached = store.tabs.filter { it.lessonId == lesson.id }
                if (attached.isEmpty()) {
                    Text(
                        "Найдите таб песни в библиотеке и нажмите «Прикрепить к уроку» — он появится здесь.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                attached.forEach { tab ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpenUrl(tab.url, lesson.id) }, modifier = Modifier.weight(1f)) {
                            Text(tab.title, maxLines = 2)
                        }
                        IconButton(onClick = { store.removeTab(tab) }) { Icon(Icons.Filled.Delete, "Удалить") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onOpenUrl(Curriculum.searchUrl(song), lesson.id) }) {
                        Text("Найти таб")
                    }
                    OutlinedButton(onClick = { onOpenUrl(Curriculum.TABS_LIBRARY_URL, lesson.id) }) {
                        Text("Библиотека")
                    }
                }
            }
        }
    }
}

@Composable
private fun BpmControls(store: AppStore, id: String, start: Int, target: Int) {
    val record = store.bpmRecords[id]
    var playing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Темп: начните с $start BPM, цель $target BPM" + (record?.let { " · рекорд $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                if (playing) {
                    MetronomeEngine.stop()
                    playing = false
                } else {
                    MetronomeEngine.stop()
                    MetronomeEngine.bpm = maxOf(start, record?.minus(10) ?: 0)
                    MetronomeEngine.trainerEnabled = target > start
                    MetronomeEngine.trainerTarget = target
                    MetronomeEngine.trainerStep = 4
                    MetronomeEngine.trainerBars = 4
                    MetronomeEngine.start()
                    playing = true
                }
            }) { Text(if (playing) "Стоп" else "▶ Метроном с разгоном") }
            OutlinedButton(onClick = { store.recordBpm(id, MetronomeEngine.currentBeat().second) }) {
                Text("Сыграл чисто")
            }
        }
    }
}
