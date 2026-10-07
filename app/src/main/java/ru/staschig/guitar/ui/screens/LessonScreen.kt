package ru.staschig.guitar.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.audio.MetronomeEngine
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.TabDoc
import ru.staschig.guitar.data.TabKind
import ru.staschig.guitar.lessons.Chord
import ru.staschig.guitar.lessons.Chords
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Exercise
import ru.staschig.guitar.lessons.Lesson
import ru.staschig.guitar.lessons.Song
import ru.staschig.guitar.lessons.Step
import ru.staschig.guitar.lessons.StepKind
import ru.staschig.guitar.lessons.StepTool
import ru.staschig.guitar.play.ExampleSynth

/**
 * Занятие — пошаговый мастер: «Шаг N из M», что делать, пример звучания, инструмент шага
 * и одна главная кнопка внизу (Начать → Пауза/Дальше → Готово).
 * Первый шаг дня — настройка гитары.
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
    onPlay: (id: String, title: String, query: String) -> Unit,
) {
    val lesson = Curriculum.byId(lessonId)
    if (lesson == null) {
        Text("Урок не найден", Modifier.padding(16.dp))
        return
    }
    val steps = lesson.steps(store.settings.dailyMinutes, store.settings.focus)
    val withTuning = rememberSaveable { !store.tunedToday() }
    val offset = if (withTuning) 1 else 0
    val total = steps.size + offset

    var index by rememberSaveable { mutableIntStateOf(0) } // == total → итог
    var started by rememberSaveable { mutableStateOf(false) }
    var running by rememberSaveable { mutableStateOf(false) }
    var remaining by rememberSaveable { mutableIntStateOf(0) }
    /** Секунды практики, ещё не записанные в журнал (записываем каждую полную минуту). */
    var pendingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var totalSeconds by rememberSaveable { mutableIntStateOf(0) }
    var confirmExit by remember { mutableStateOf(false) }
    val wide = isWide()

    val step: Step? = (index - offset).takeIf { index in offset until total }?.let { steps[it] }
    val tool = step?.tool
    val beeper = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            totalSeconds++
            pendingSeconds++
            // Минуты пишутся сразу, поэтому уход из урока ничего не теряет и не удваивает.
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
    LaunchedEffect(index) {
        val t = tool
        if (t is StepTool.Metronome && step != null) {
            MetronomeEngine.stop()
            val record = store.bpmRecords[step.id]
            MetronomeEngine.bpm = maxOf(t.bpmStart, record?.minus(10) ?: 0)
            MetronomeEngine.subdivision = 1
            MetronomeEngine.beatsPerBar = 4
            MetronomeEngine.trainerEnabled = t.bpmTarget > MetronomeEngine.bpm
            MetronomeEngine.trainerTarget = t.bpmTarget
            MetronomeEngine.trainerStep = 4
            MetronomeEngine.trainerBars = 4
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MetronomeEngine.stop()
            TonePlayer.stop()
            beeper?.release()
        }
    }

    fun next() {
        MetronomeEngine.stop()
        TonePlayer.stop()
        running = false
        started = false
        if (index == offset - 1) store.markTuned()
        index++
        if (index == total) {
            if (pendingSeconds >= 30) store.addPracticeMinutes(1)
            pendingSeconds = 0
            store.completeLesson(lesson.id)
        } else {
            remaining = steps[index - offset].minutes * 60
        }
    }

    fun start() {
        started = true
        running = true
        if (remaining == 0) remaining = step?.minutes?.times(60) ?: 0
        if (tool is StepTool.Metronome) MetronomeEngine.start()
    }

    fun pause() {
        running = false
        MetronomeEngine.stop()
    }

    BackHandler { if (index in 1 until total) confirmExit = true else onBack() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Урок ${lesson.number}: ${lesson.technique.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(lesson.level.title, style = MaterialTheme.typography.bodySmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = { if (index in 1 until total) confirmExit = true else onBack() }) {
                    Icon(Icons.Filled.Close, "Выйти из занятия")
                }
            },
        )
        if (index >= total) {
            FinishView(store, lesson, totalSeconds, onHome = onBack)
            return@Column
        }
        val header: @Composable () -> Unit = {
            StepProgress(index + 1, total, Modifier.padding(horizontal = 16.dp))
            Text(
                if (step == null) "Настройте гитару" else "${stepName(step.kind)}: ${step.title}",
                style = if (wide) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // Одна главная кнопка внизу — что делать дальше.
        val actions: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    step == null -> {
                        BigButton("Гитара настроена — дальше", { next() })
                        TextButton(onClick = { next() }, modifier = Modifier.fillMaxWidth()) { Text("Пропустить") }
                    }
                    !started -> BigButton(
                        if (tool is StepTool.Metronome) "▶  Начать с метрономом · ${step.minutes} мин" else "▶  Начать · ${step.minutes} мин",
                        { start() },
                    )
                    remaining == 0 -> BigButton(if (index == total - 1) "✓  Завершить занятие" else "Готово — дальше →", { next() })
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "%d:%02d".format(remaining / 60, remaining % 60),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(if (running) "идёт шаг" else "пауза", style = MaterialTheme.typography.bodySmall)
                        }
                        if (running) OutlinedButton(onClick = { pause() }) { Text("Пауза") }
                        else FilledTonalButton(onClick = { start() }) { Text("Продолжить") }
                        TextButton(onClick = { next() }) { Text(if (index == total - 1) "Завершить" else "Дальше →") }
                    }
                }
            }
        }

        if (wide) {
            // Телефон лёжа: слева — шаг, указания и кнопка; справа — инструмент или таб.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    header()
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            step == null -> TuneInfo()
                            tool == StepTool.SongTabs -> SongInfo(step.song!!)
                            else -> ExerciseInfo(step.exercise!!, tool!!, onOpenPiece, onPlay)
                        }
                    }
                    HorizontalDivider()
                    actions()
                }
                VerticalDivider()
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    when {
                        step == null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            TunerPanel(store, Modifier.fillMaxWidth())
                        }
                        tool == StepTool.SongTabs -> SongStep(store, lesson, step.song!!, onOpenUrl, onOpenDoc, onPlay)
                        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            ExerciseToolCard(store, step, tool!!)
                        }
                    }
                }
            }
        } else {
            header()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    step == null -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TuneInfo()
                        TunerPanel(store, Modifier.fillMaxWidth())
                    }
                    tool == StepTool.SongTabs -> SongStep(store, lesson, step.song!!, onOpenUrl, onOpenDoc, onPlay)
                    else -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExerciseInfo(step.exercise!!, tool!!, onOpenPiece, onPlay)
                        ExerciseToolCard(store, step, tool)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            HorizontalDivider()
            actions()
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Выйти из занятия?") },
            text = { Text("Время практики уже записано. Урок можно пройти заново в любой момент.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Продолжить занятие") } },
        )
    }
}

private fun stepName(kind: StepKind) = when (kind) {
    StepKind.WARMUP -> "Разминка"
    StepKind.TECHNIQUE -> "Упражнение"
    StepKind.SONG -> "Песня"
}

/** Шаг 0: как настроить гитару (сам тюнер — рядом). */
@Composable
private fun TuneInfo() {
    NumberedSteps(
        listOf(
            "Дёрните открытую струну — тюнер сам определит какую.",
            "Стрелка влево — подтяните колок, вправо — ослабьте.",
            "Зелёный цвет — струна настроена. Пройдите все 6 струн.",
        )
    )
}

/** Шаг-упражнение: что делать + как должно звучать + таб. */
@Composable
private fun ExerciseInfo(
    ex: Exercise,
    tool: StepTool,
    onOpenPiece: (String) -> Unit,
    onPlay: (String, String, String) -> Unit,
) {
    val interactive = Curriculum.interactiveTab(ex.id)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Что делать", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            NumberedSteps(toInstructions(ex.description))
            Text("Методика: ${ex.school}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
    when {
        interactive != null -> {
            TabExampleButton(pieceQuery(interactive))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onOpenPiece(interactive) }) { Text("Ноты и таб") }
                TextButton(onClick = { onPlay(interactive, ex.title, pieceQuery(interactive)) }) { Text("🎮 Играть с проверкой") }
            }
        }
        ex.id == "t_strum" -> ExampleButton {
            ExampleSynth.renderChords(List(4) { Chords.byName("G")!! }, (tool as? StepTool.Metronome)?.bpmStart ?: 70)
        }
    }
    ex.tab?.let { TabText(it) }
}

/** Инструмент шага-упражнения: метроном, тренажёр смен или тюнер. */
@Composable
private fun ExerciseToolCard(store: AppStore, step: Step, tool: StepTool) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tool) {
                is StepTool.Metronome -> {
                    val record = store.bpmRecords[step.id]
                    Text(
                        "Метроном: начните с ${tool.bpmStart} BPM" +
                            (if (tool.bpmTarget > tool.bpmStart) ", он сам ускорится до ${tool.bpmTarget}" else "") +
                            (record?.let { " · ваш рекорд $it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    MetronomePanel(Modifier.fillMaxWidth(), compact = true, onRecord = { store.recordBpm(step.id, it) })
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

/** Шаг-песня: что делать (слева в горизонтальном режиме). */
@Composable
private fun SongInfo(song: Song) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Что делать", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            NumberedSteps(
                listOf(
                    "Послушайте пример — кнопка «🔊» справа.",
                    "Сыграйте медленно по табу или по аккордам.",
                    "Получается — включите метроном или «Играть с проверкой».",
                )
            )
        }
    }
    Text("Ритм: ${song.strumming}", style = MaterialTheme.typography.titleSmall)
    Text(song.description, style = MaterialTheme.typography.bodyMedium)
}

/** Итог занятия. */
@Composable
private fun FinishView(store: AppStore, lesson: Lesson, seconds: Int, onHome: () -> Unit) {
    val s = store.settings
    val next = store.nextLesson()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("✓", fontSize = 72.sp, color = MaterialTheme.colorScheme.tertiary)
        Text("Занятие завершено!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Урок ${lesson.number} пройден · ${(seconds + 30) / 60} мин в этом занятии\n" +
                "Сегодня ${store.minutesToday()} из ${s.dailyMinutes} мин · серия ${store.streak()} дн.",
            textAlign = TextAlign.Center,
        )
        next?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Следующий урок", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Урок ${it.number}: ${it.technique.title}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BigButton("На главную", onHome)
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
    onPlay: (String, String, String) -> Unit,
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
            Text("Выберите, по чему играть:", style = MaterialTheme.typography.labelLarge)
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
                Row {
                    TextButton(onClick = { onOpenDoc(selected.doc.id) }) { Text("На весь экран") }
                    if (selected.doc.kind == TabKind.GP) {
                        TextButton(onClick = { onPlay("doc_" + selected.doc.id, selected.doc.title, docQuery(selected.doc)) }) {
                            Text("🎮 Играть с проверкой")
                        }
                    }
                }
            }
            is SongSource.Piece -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { TabExampleButton(pieceQuery(selected.asset), label = "🔊 Пример") }
                    OutlinedButton(onClick = { onPlay(selected.asset, Curriculum.pieceTitle(selected.asset), pieceQuery(selected.asset)) }) {
                        Text("🎮 С проверкой")
                    }
                }
                AlphaTabView(pieceQuery(selected.asset), Modifier.weight(1f).fillMaxWidth())
            }
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
                val known = song.chords.mapNotNull { Chords.byName(it) }
                if (known.isNotEmpty()) ChordsExampleButton(known, song.bpm ?: 90)
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
