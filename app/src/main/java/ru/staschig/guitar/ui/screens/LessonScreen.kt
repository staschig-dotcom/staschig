package ru.staschig.guitar.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.audio.MetronomeEngine
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.TabDoc
import ru.staschig.guitar.lessons.Chord
import ru.staschig.guitar.lessons.Chords
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Exercise
import ru.staschig.guitar.lessons.Lesson
import ru.staschig.guitar.lessons.Song
import ru.staschig.guitar.lessons.Step
import ru.staschig.guitar.lessons.StepKind
import ru.staschig.guitar.lessons.StepTool

/**
 * Урок в режиме «ведущего»: шаги — вкладки, на каждом шаге сразу открыт его инструмент
 * (метроном, тренажёр аккордов, тюнер или табы песни). Кнопка таймера запускает и метроном.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    store: AppStore,
    lessonId: String,
    onBack: () -> Unit,
    onOpenUrl: (url: String, lessonId: String?) -> Unit,
    onOpenPiece: (String) -> Unit,
    onOpenDoc: (String) -> Unit,
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
    /** Секунды практики, ещё не записанные в журнал (записываем каждую полную минуту). */
    var pendingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var totalSeconds by rememberSaveable { mutableIntStateOf(0) }

    val step = steps[current]
    val tool = step.tool
    val beeper = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            totalSeconds++
            pendingSeconds++
            // Минуты пишутся сразу, поэтому уход из урока (в браузер, за табом) ничего не теряет и не удваивает.
            if (pendingSeconds >= 60) {
                store.addPracticeMinutes(1)
                pendingSeconds -= 60
            }
            if (remaining > 0) {
                remaining--
                if (remaining == 0) beeper?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            }
        }
    }

    // Метроном шага заранее выставлен на стартовый темп упражнения (или чуть ниже рекорда).
    LaunchedEffect(current) {
        val t = tool
        if (t is StepTool.Metronome) {
            MetronomeEngine.stop()
            val record = store.bpmRecords[step.id]
            MetronomeEngine.bpm = maxOf(t.bpmStart, record?.minus(10) ?: 0)
            MetronomeEngine.subdivision = 1
            MetronomeEngine.beatsPerBar = 4
            MetronomeEngine.trainerEnabled = t.bpmTarget > MetronomeEngine.bpm
            MetronomeEngine.trainerTarget = t.bpmTarget
            MetronomeEngine.trainerStep = 4
            MetronomeEngine.trainerBars = 4
            if (running) MetronomeEngine.start()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MetronomeEngine.stop()
            beeper?.release()
        }
    }

    fun goTo(index: Int) {
        MetronomeEngine.stop()
        current = index
        remaining = steps[index].minutes * 60
    }

    fun toggleTimer() {
        running = !running
        if (tool is StepTool.Metronome) {
            if (running) MetronomeEngine.start() else MetronomeEngine.stop()
        }
    }

    fun finish() {
        running = false
        if (pendingSeconds >= 30) store.addPracticeMinutes(1)
        pendingSeconds = 0
        store.completeLesson(lesson.id)
        onBack()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Урок ${lesson.number} · ${lesson.level.title}", maxLines = 1)
                    Text(lesson.technique.title, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
        TabRow(selectedTabIndex = current) {
            steps.forEachIndexed { i, st ->
                Tab(
                    selected = i == current,
                    onClick = { goTo(i) },
                    text = { Text("${shortTitle(st.kind)} ${st.minutes}′", maxLines = 1) },
                )
            }
        }
        TimerBar(
            remaining = remaining,
            running = running,
            totalMinutes = totalSeconds / 60,
            toolHint = toolHint(tool),
            isLast = current == steps.lastIndex,
            onToggle = { toggleTimer() },
            onNext = { if (current < steps.lastIndex) goTo(current + 1) else finish() },
        )
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tool) {
                StepTool.SongTabs -> SongStep(store, lesson, step.song!!, onOpenUrl, onOpenDoc)
                else -> ExerciseStep(store, step, step.exercise!!, tool, onOpenPiece)
            }
        }
    }
}

private fun shortTitle(kind: StepKind) = when (kind) {
    StepKind.WARMUP -> "Разминка"
    StepKind.TECHNIQUE -> "Упражнение"
    StepKind.SONG -> "Песня"
}

private fun toolHint(tool: StepTool) = when (tool) {
    is StepTool.Metronome -> "▶ запустит таймер и метроном"
    is StepTool.ChordChanges -> "Смены аккордов: старт — внизу"
    StepTool.Tuner -> "Сначала настройте гитару"
    StepTool.SongTabs -> "Играйте по табу"
}

@Composable
private fun TimerBar(
    remaining: Int,
    running: Boolean,
    totalMinutes: Int,
    toolHint: String,
    isLast: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Старт/пауза")
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (remaining > 0) "%d:%02d".format(remaining / 60, remaining % 60) else "Время блока вышло",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (remaining > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
            Text(
                "$toolHint · сегодня в уроке $totalMinutes мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (remaining == 0 || isLast) {
            Button(onClick = onNext) { Text(if (isLast) "Завершить" else "Дальше →") }
        } else {
            TextButton(onClick = onNext) { Text("Дальше →") }
        }
    }
}

/** Шаг-упражнение: описание + встроенный инструмент шага. */
@Composable
private fun ExerciseStep(
    store: AppStore,
    step: Step,
    ex: Exercise,
    tool: StepTool,
    onOpenPiece: (String) -> Unit,
) {
    var showDetails by rememberSaveable(ex.id) { mutableStateOf(true) }
    val interactive = Curriculum.interactiveTab(ex.id)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(ex.title, style = MaterialTheme.typography.titleLarge)
        Text("Школа: ${ex.school}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        if (showDetails) Text(ex.description)
        TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Скрыть описание" else "Показать описание") }
        ex.tab?.let { TabText(it) }
        interactive?.let {
            FilledTonalButton(onClick = { onOpenPiece(it) }) { Text("▶ Ноты, таб и звук упражнения") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tool) {
                    is StepTool.Metronome -> {
                        val record = store.bpmRecords[step.id]
                        Text(
                            "Метроном: начните с ${tool.bpmStart} BPM" +
                                (if (tool.bpmTarget > tool.bpmStart) ", разгон до ${tool.bpmTarget}" else "") +
                                (record?.let { " · рекорд $it" } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        MetronomePanel(
                            Modifier.fillMaxWidth(),
                            compact = true,
                            onRecord = { store.recordBpm(step.id, it) },
                        )
                    }
                    is StepTool.ChordChanges -> {
                        Text("Тренажёр смен аккордов", style = MaterialTheme.typography.titleSmall)
                        ChordChangesPanel(store, tool.pairs, Modifier.fillMaxWidth())
                    }
                    StepTool.Tuner -> {
                        Text("Тюнер", style = MaterialTheme.typography.titleSmall)
                        TunerPanel(store, Modifier.fillMaxWidth())
                    }
                    StepTool.SongTabs -> Unit
                }
            }
        }
    }
}

private sealed interface SongSource {
    data class Doc(val doc: TabDoc) : SongSource
    data class Piece(val asset: String) : SongSource
    data object ChordsAndRhythm : SongSource
}

/** Шаг-песня: сразу открыт таб (сохранённый файл → встроенный таб → аккорды и ритм). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongStep(
    store: AppStore,
    lesson: Lesson,
    song: Song,
    onOpenUrl: (String, String?) -> Unit,
    onOpenDoc: (String) -> Unit,
) {
    val docs = store.library.docs.filter { it.lessonId == lesson.id }
    val piece = Curriculum.interactiveTab(song.id)
    val sources = docs.map { SongSource.Doc(it) } +
        listOfNotNull(piece?.let { SongSource.Piece(it) }) +
        SongSource.ChordsAndRhythm
    var selectedIndex by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    val selected = sources[selectedIndex.coerceIn(0, sources.lastIndex)]
    var shownChord by remember { mutableStateOf<Chord?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${song.artist} — ${song.title}", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEachIndexed { i, src ->
                    FilterChip(
                        selected = src == selected,
                        onClick = { selectedIndex = i },
                        label = {
                            Text(
                                when (src) {
                                    is SongSource.Doc -> "📥 ${src.doc.title}"
                                    is SongSource.Piece -> "🎼 Интерактивный таб"
                                    SongSource.ChordsAndRhythm -> "Аккорды и ритм"
                                },
                                maxLines = 1,
                            )
                        },
                    )
                }
                AssistChip(onClick = { onOpenUrl(Curriculum.searchUrl(song), lesson.id) }, label = { Text("＋ Найти таб") })
            }
        }
        when (selected) {
            is SongSource.Doc -> Column(Modifier.fillMaxSize()) {
                DocContent(store, selected.doc, Modifier.weight(1f).fillMaxWidth())
                TextButton(onClick = { onOpenDoc(selected.doc.id) }) { Text("Открыть на весь экран") }
            }
            is SongSource.Piece -> AlphaTabView(pieceQuery(selected.asset), Modifier.fillMaxSize())
            SongSource.ChordsAndRhythm -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    song.chords.forEach { name ->
                        val chord = Chords.byName(name)
                        if (chord != null) {
                            Box(Modifier.padding(4.dp)) {
                                OutlinedButton(onClick = { shownChord = chord }) { ChordDiagram(chord, width = 84.dp) }
                            }
                        } else {
                            AssistChip(onClick = {}, label = { Text(name) })
                        }
                    }
                }
                Text("Ритм: ${song.strumming}", style = MaterialTheme.typography.titleSmall)
                Text(song.description)
                song.tab?.let { TabText(it) }
                if (docs.isEmpty() && piece == null) {
                    Text(
                        "Таба этой песни в приложении пока нет. Нажмите «＋ Найти таб», на странице таба — ⬇: " +
                            "он сохранится, привяжется к уроку и будет открываться здесь сразу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val links = store.tabs.filter { it.lessonId == lesson.id }
                if (links.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        links.forEach { l -> AssistChip(onClick = { onOpenUrl(l.url, lesson.id) }, label = { Text("🔗 ${l.title}", maxLines = 1) }) }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Метроном для ритма песни", style = MaterialTheme.typography.titleSmall)
                        LaunchedEffect(song.id) {
                            song.bpm?.let {
                                MetronomeEngine.bpm = (it * 0.7).toInt()
                                MetronomeEngine.trainerTarget = it
                                MetronomeEngine.trainerEnabled = true
                            }
                        }
                        MetronomePanel(Modifier.fillMaxWidth(), compact = true, onRecord = { store.recordBpm(song.id, it) })
                    }
                }
            }
        }
    }

    shownChord?.let { c ->
        AlertDialog(
            onDismissRequest = { shownChord = null },
            confirmButton = { TextButton(onClick = { shownChord = null }) { Text("Закрыть") } },
            dismissButton = {
                TextButton(onClick = { TonePlayer.playSamples(GuitarSynth.render(c.midi)) }) { Text("🔊 Послушать") }
            },
            text = { ChordDiagram(c, Modifier.fillMaxWidth(), width = 160.dp) },
        )
    }
}
