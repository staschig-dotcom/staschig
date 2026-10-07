package ru.staschig.guitar.ui.screens

import android.content.Intent
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONObject
import ru.staschig.guitar.audio.GuitarSynth
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.TabDoc
import ru.staschig.guitar.tabedit.EdBeat
import ru.staschig.guitar.tabedit.EdNote
import ru.staschig.guitar.tabedit.EdScore
import ru.staschig.guitar.tabedit.NoteEffect
import java.io.File

private val DURATIONS = listOf(1 to "𝅝 1", 2 to "𝅗𝅥 1/2", 4 to "♩ 1/4", 8 to "♪ 1/8", 16 to "𝅘𝅥𝅯 1/16", 32 to "1/32")
private val STRING_NAMES = listOf("e", "B", "G", "D", "A", "E")

/**
 * Редактор таба: курсор (удар × струна) на дорожке, клавиатура ладов, длительности и приёмы.
 * Сохраняется как alphaTex (ноты, таб, звук, игра с проверкой) и экспортируется в Guitar Pro (.gp).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TabEditorScreen(store: AppStore, docId: String?, onBack: () -> Unit, onOpenDoc: (String) -> Unit) {
    val context = LocalContext.current
    val lib = store.library
    val existing = docId?.let { lib.byId(it) }
    var score by remember {
        mutableStateOf(existing?.text?.let { runCatching { EdScore.fromJson(it) }.getOrNull() } ?: EdScore())
    }
    var savedId by rememberSaveable { mutableStateOf(docId) }
    var dirty by remember { mutableStateOf(false) }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var string by rememberSaveable { mutableIntStateOf(1) }
    var effect by remember { mutableStateOf(NoteEffect.NONE) }
    var showInfo by remember { mutableStateOf(existing == null) }
    var confirmExit by remember { mutableStateOf(false) }
    var exportRequest by remember { mutableStateOf<String?>(null) }
    val wide = isWide()

    fun update(s: EdScore) {
        score = s
        dirty = true
    }
    fun beat() = score.beats[sel.coerceIn(0, score.beats.lastIndex)]
    fun setBeat(b: EdBeat) = update(score.copy(beats = score.beats.toMutableList().also { it[sel] = b }))

    fun putFret(fret: Int) {
        val note = EdNote(string, fret, effect)
        setBeat(beat().withNote(note))
        // Звук ноты — чтобы сразу слышать, что набрано.
        val midi = score.tuning[string - 1] + fret
        TonePlayer.playSamples(GuitarSynth.render(listOf(midi), seconds = 0.8f))
    }

    fun next() {
        if (sel == score.beats.lastIndex) {
            update(score.copy(beats = score.beats + EdBeat(beat().duration, beat().dotted)))
        }
        sel++
    }

    fun save(): TabDoc {
        val doc = lib.saveEdited(savedId, score.title, score.artist, score.toAlphaTex(), score.toJson())
        savedId = doc.id
        dirty = false
        return doc
    }

    BackHandler { if (dirty) confirmExit = true else onBack() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(score.title.ifBlank { "Новый таб" }, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = { if (dirty) confirmExit = true else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
            },
            actions = {
                TextButton(onClick = { showInfo = true }) { Text("Название и темп") }
                TextButton(onClick = {
                    save()
                    Toast.makeText(context, "Сохранено в «Мои табы»", Toast.LENGTH_SHORT).show()
                }) { Text("Сохранить") }
            },
        )

        // Дорожка таба
        TabStrip(
            score = score,
            selected = sel,
            string = string,
            onSelect = { b, s -> sel = b; string = s },
            modifier = Modifier.fillMaxWidth().height(if (wide) 150.dp else 180.dp),
        )
        val b = beat()
        Text(
            "Удар ${sel + 1} из ${score.beats.size} · струна ${STRING_NAMES[string - 1]} (${string}-я) · " +
                (if (b.isRest) "пауза" else b.notes.joinToString { "${STRING_NAMES[it.string - 1]}:${it.fret}" }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        HorizontalDivider()

        // Панель ввода
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Лад (нажмите — нота встанет на выбранную струну)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..24).forEach { f ->
                    val current = b.noteOn(string)?.fret == f
                    FilledTonalButton(
                        onClick = { putFret(f) },
                        modifier = Modifier.size(width = 48.dp, height = 40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        colors = if (current) androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
                    ) { Text("$f", fontWeight = FontWeight.Bold) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (sel > 0) sel-- }) { Text("◀ Назад") }
                Button(onClick = { next() }) { Text("Следующий удар ▶") }
                OutlinedButton(onClick = { setBeat(b.withoutNote(string)) }) { Text("Стереть ноту") }
            }

            Text("Длительность удара", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DURATIONS.forEach { (d, label) ->
                    FilterChip(b.duration == d, { setBeat(b.copy(duration = d)) }, { Text(label) })
                }
                FilterChip(b.dotted, { setBeat(b.copy(dotted = !b.dotted)) }, { Text("с точкой") })
                FilterChip(b.isRest, { setBeat(b.copy(notes = emptyList())) }, { Text("пауза") })
            }

            Text("Приём для следующих нот", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NoteEffect.entries.forEach { e ->
                    FilterChip(effect == e, {
                        effect = e
                        // Если на струне уже стоит нота — сразу применяем приём к ней.
                        b.noteOn(string)?.let { setBeat(b.withNote(it.copy(effect = e))) }
                    }, { Text(e.title) })
                }
            }

            Text("Удары", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    update(score.copy(beats = score.beats.toMutableList().also { it.add(sel + 1, EdBeat(b.duration, b.dotted)) }))
                    sel++
                }) { Text("＋ Вставить после") }
                OutlinedButton(onClick = {
                    update(score.copy(beats = score.beats.toMutableList().also { it.add(sel + 1, b) }))
                    sel++
                }) { Text("Повторить удар") }
                OutlinedButton(enabled = score.beats.size > 1, onClick = {
                    update(score.copy(beats = score.beats.toMutableList().also { it.removeAt(sel) }))
                    sel = sel.coerceAtMost(score.beats.lastIndex)
                }) { Text("Удалить удар") }
                OutlinedButton(onClick = {
                    val midi = b.notes.map { score.tuning[it.string - 1] + it.fret }
                    if (midi.isNotEmpty()) TonePlayer.playSamples(GuitarSynth.render(midi, seconds = 1.5f))
                }) { Text("🔊 Удар") }
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenDoc(save().id) }, modifier = Modifier.weight(1f)) { Text("Сохранить и открыть (ноты, звук, игра)") }
                OutlinedButton(onClick = { exportRequest = docQuery(save()) }) { Text("Экспорт .gp") }
            }
        }
    }

    // Экспорт в Guitar Pro: alphaTab в скрытом WebView делает файл .gp, его можно отправить/сохранить.
    exportRequest?.let { q ->
        ScoreExtractor("$q&export=gp") { json ->
            exportRequest = null
            val b64 = runCatching { JSONObject(json).getString("gp") }.getOrNull()
            if (b64 == null) {
                Toast.makeText(context, "Не удалось сделать файл Guitar Pro", Toast.LENGTH_LONG).show()
                return@ScoreExtractor
            }
            val dir = File(lib.dir, "export").apply { mkdirs() }
            val safe = score.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").ifBlank { "tab" }
            val file = File(dir, "$safe.gp").apply { writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Сохранить или отправить ${file.name}"))
        }
    }

    if (showInfo) InfoDialog(score, onDone = { update(it); showInfo = false }, onDismiss = { showInfo = false })

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Сохранить таб?") },
            confirmButton = { TextButton(onClick = { save(); confirmExit = false; onBack() }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Не сохранять") } },
        )
    }
}

/** Дорожка таба: 6 линий, удары столбцами, такты, курсор. Нажатие выбирает удар и струну. */
@Composable
private fun TabStrip(
    score: EdScore,
    selected: Int,
    string: Int,
    onSelect: (beat: Int, string: Int) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val colPx = with(density) { 46.dp.toPx() }
    val leftPx = with(density) { 28.dp.toPx() }
    val barGapPx = with(density) { 14.dp.toPx() }
    val bars = score.bars()
    // x-позиция каждого удара с учётом промежутков между тактами
    val xs = FloatArray(score.beats.size)
    var x = leftPx + barGapPx
    bars.forEach { bar ->
        bar.forEach { i -> xs[i] = x; x += colPx }
        x += barGapPx
    }
    val totalWidth = with(density) { (x + colPx).toDp() }
    val scroll = rememberScrollState()
    LaunchedEffect(selected) {
        val target = (xs.getOrElse(selected) { 0f } - 200f).toInt().coerceAtLeast(0)
        scroll.animateScrollTo(target)
    }
    val measurer = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val fretStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
    val smallStyle = TextStyle(fontSize = 10.sp, color = cs.primary)
    val labelStyle = TextStyle(fontSize = 12.sp, color = cs.onSurfaceVariant)

    Box(modifier.background(cs.surfaceVariant).horizontalScroll(scroll)) {
        Canvas(
            Modifier.width(totalWidth).fillMaxHeight().pointerInput(score.beats.size, xs.contentHashCode()) {
                detectTapGestures { p ->
                    val top = 26.dp.toPx()
                    val gap = (size.height - top - 18.dp.toPx()) / 5
                    val s = ((p.y - top) / gap).let { Math.round(it) }.coerceIn(0, 5) + 1
                    val idx = xs.indices.minByOrNull { kotlin.math.abs(xs[it] + colPx / 2 - p.x) } ?: 0
                    onSelect(idx, s)
                }
            },
        ) {
            val top = 26.dp.toPx()
            val gap = (size.height - top - 18.dp.toPx()) / 5
            fun y(s: Int) = top + (s - 1) * gap
            // курсор
            if (selected in xs.indices) {
                drawRoundRect(cs.primary.copy(alpha = 0.18f), Offset(xs[selected], top - 14f), Size(colPx, gap * 5 + 28f), CornerRadius(10f))
                drawRoundRect(cs.primary, Offset(xs[selected] + 4f, y(string) - 16f), Size(colPx - 8f, 32f), CornerRadius(8f))
            }
            for (s in 1..6) {
                drawLine(cs.onSurfaceVariant.copy(alpha = 0.6f), Offset(leftPx, y(s)), Offset(size.width, y(s)), 2f)
                drawText(measurer, STRING_NAMES[s - 1], Offset(6f, y(s) - 16f), labelStyle)
            }
            // такты
            var barX = leftPx + barGapPx / 2
            bars.forEachIndexed { bi, bar ->
                drawLine(cs.onSurfaceVariant, Offset(barX, y(1)), Offset(barX, y(6)), 3f)
                drawText(measurer, "${bi + 1}", Offset(barX + 4f, 0f), smallStyle)
                barX += bar.size * colPx + barGapPx
            }
            drawLine(cs.onSurfaceVariant, Offset(barX, y(1)), Offset(barX, y(6)), 3f)
            // ноты и длительности
            score.beats.forEachIndexed { i, b ->
                val cx = xs[i] + colPx / 2
                if (b.isRest) {
                    val t = measurer.measure("пауза", labelStyle.copy(fontSize = 10.sp))
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f, y(3) + gap / 2 - t.size.height / 2f))
                }
                b.notes.forEach { n ->
                    val label = (if (n.effect == NoteEffect.DEAD) "x" else "${n.fret}") + n.effect.short.takeIf { n.effect != NoteEffect.DEAD }.orEmpty()
                    val t = measurer.measure(label, fretStyle)
                    val isCursor = i == selected && n.string == string
                    drawRect(if (isCursor) cs.primary else cs.surfaceVariant, Offset(cx - t.size.width / 2f - 2f, y(n.string) - t.size.height / 2f), Size(t.size.width + 4f, t.size.height.toFloat()))
                    drawText(t, topLeft = Offset(cx - t.size.width / 2f, y(n.string) - t.size.height / 2f))
                }
                val d = measurer.measure(durationMark(b), smallStyle)
                drawText(d, topLeft = Offset(cx - d.size.width / 2f, y(6) + 4f))
            }
        }
    }
}

private fun durationMark(b: EdBeat) = when (b.duration) {
    1 -> "1"; 2 -> "½"; 4 -> "¼"; 8 -> "⅛"; 16 -> "1/16"; else -> "1/${b.duration}"
} + if (b.dotted) "." else ""

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoDialog(score: EdScore, onDone: (EdScore) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(score.title) }
    var artist by remember { mutableStateOf(score.artist) }
    var tempo by remember { mutableStateOf(score.tempo.toString()) }
    var ts by remember { mutableStateOf(score.beatsPerBar to score.beatUnit) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таб") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Исполнитель") }, singleLine = true)
                OutlinedTextField(
                    tempo, { tempo = it.filter(Char::isDigit).take(3) }, label = { Text("Темп, BPM") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Размер", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4 to 4, 3 to 4, 2 to 4, 6 to 8, 12 to 8).forEach { v ->
                        FilterChip(ts == v, { ts = v }, { Text("${v.first}/${v.second}") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone(
                    score.copy(
                        title = title.ifBlank { "Мой таб" },
                        artist = artist,
                        tempo = (tempo.toIntOrNull() ?: score.tempo).coerceIn(30, 300),
                        beatsPerBar = ts.first,
                        beatUnit = ts.second,
                    )
                )
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
