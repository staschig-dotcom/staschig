package ru.staschig.guitar.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.staschig.guitar.audio.BackingPlayer
import ru.staschig.guitar.audio.MetronomeEngine
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.TabDoc
import ru.staschig.guitar.data.TabKind
import ru.staschig.guitar.data.TabLibrary

/** Выбор аудиофайла с телефона и копирование в библиотеку (в фоне). */
@Composable
fun rememberAudioImporter(store: AppStore, lessonId: String? = null, onImported: (TabDoc) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val cr = context.contentResolver
                var name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "track"
                if (TabLibrary.kindOf(name) != TabKind.AUDIO) {
                    val ext = TabLibrary.extensionForMime(cr.getType(uri))
                    if (ext != null && TabLibrary.kindOf("x.$ext") == TabKind.AUDIO) name = "$name.$ext"
                }
                if (TabLibrary.kindOf(name) != TabKind.AUDIO) error("формат не поддерживается: ${name.substringAfterLast('.', "?")}")
                val doc = withContext(Dispatchers.IO) {
                    store.library.copyIn(name, cr.openInputStream(uri)!!, lessonId)
                }
                store.library.save(doc)
                doc
            }.onSuccess(onImported)
                .onFailure { Toast.makeText(context, "Не удалось загрузить: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    return { launcher.launch(arrayOf("audio/*")) }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Панель минусовки: выбор трека, ▶/❚❚, перемотка, скорость, транспонирование, повтор A–B.
 * [lessonId] — показывать прежде всего минусовки этого урока и привязывать к нему новые.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackingPanel(
    store: AppStore,
    modifier: Modifier = Modifier,
    lessonId: String? = null,
    compact: Boolean = false,
    initialId: String? = null,
) {
    val context = LocalContext.current
    val all = store.library.docs.filter { it.kind == TabKind.AUDIO }
    val tracks = all.sortedByDescending { lessonId != null && it.lessonId == lessonId }
    var selectedId by remember { mutableStateOf(initialId ?: tracks.firstOrNull()?.id) }
    val selected = tracks.firstOrNull { it.id == selectedId } ?: tracks.firstOrNull()
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(BackingPlayer.speed) }
    var semis by remember { mutableIntStateOf(BackingPlayer.semitones) }
    var a by remember { mutableStateOf(BackingPlayer.loopA) }
    var b by remember { mutableStateOf(BackingPlayer.loopB) }
    var error by remember { mutableStateOf(false) }
    val import = rememberAudioImporter(store, lessonId) { selectedId = it.id }

    LaunchedEffect(selected?.id) {
        selected?.let { d -> store.library.file(d)?.let { BackingPlayer.load(context, it) } }
        a = BackingPlayer.loopA; b = BackingPlayer.loopB
    }
    LaunchedEffect(Unit) {
        while (true) {
            BackingPlayer.tick()
            playing = BackingPlayer.isPlaying
            pos = BackingPlayer.position
            dur = BackingPlayer.duration
            error = BackingPlayer.hasError
            delay(50)
        }
    }
    DisposableEffect(Unit) { onDispose { BackingPlayer.pause() } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (tracks.isEmpty()) {
            Text(
                "Минусовок пока нет. Загрузите аудио с телефона: MP3, M4A/AAC, OGG, Opus, FLAC, WAV.",
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(onClick = import) { Text("🎧 Загрузить минусовку") }
            return@Column
        }
        if (tracks.size > 1 || !compact) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tracks.forEach { t ->
                    FilterChip(t.id == selected?.id, { selectedId = t.id }, { Text(t.title, maxLines = 1) })
                }
                TextButton(onClick = import) { Text("＋ Загрузить") }
            }
        }
        if (error) Text("Этот файл не воспроизводится на телефоне — попробуйте MP3 или M4A.", color = MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = {
                MetronomeEngine.stop()
                BackingPlayer.playPause()
            }) { Text(if (playing) "❚❚" else "▶", fontWeight = FontWeight.Bold) }
            Text(" ${time(pos)} / ${time(dur)}", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = if (dur > 0) pos.toFloat() / dur else 0f,
            onValueChange = { BackingPlayer.seekTo((it * dur).toLong()) },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f).forEach { v ->
                FilterChip(speed == v, { BackingPlayer.setSpeed(v); speed = v }, { Text("${(v * 100).toInt()}%") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Тон:")
            OutlinedButton(onClick = { BackingPlayer.setSemitones(semis - 1); semis = BackingPlayer.semitones }) { Text("−½") }
            Text(if (semis == 0) "как в оригинале" else "%+d пол.".format(semis), fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { BackingPlayer.setSemitones(semis + 1); semis = BackingPlayer.semitones }) { Text("+½") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Повтор:")
            OutlinedButton(onClick = { BackingPlayer.loopA = pos; a = pos; if ((b ?: 0) <= pos) { BackingPlayer.loopB = null; b = null } }) {
                Text("A " + (a?.let { time(it) } ?: "—"))
            }
            OutlinedButton(enabled = a != null, onClick = { BackingPlayer.loopB = pos; b = pos }) {
                Text("B " + (b?.let { time(it) } ?: "—"))
            }
            if (a != null || b != null) TextButton(onClick = { BackingPlayer.loopA = null; BackingPlayer.loopB = null; a = null; b = null }) { Text("Сбросить") }
        }
        if (!compact) {
            Text(
                "Скорость меняется без изменения тона. «Тон» сдвигает высоту — например, на −½, чтобы играть в стандартном строе под запись в строе Eb.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Экран «Минусовки»: список загруженных аудио и проигрыватель. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackingScreen(store: AppStore, onBack: () -> Unit) {
    val tracks = store.library.docs.filter { it.kind == TabKind.AUDIO }
    val import = rememberAudioImporter(store) { }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Минусовки") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigButton("＋  Загрузить аудио с телефона", import)
            Text(
                "Поддерживаются MP3, M4A/AAC, OGG, Opus, FLAC, WAV. WMA и Apple Lossless (ALAC) Android не воспроизводит — их нужно сначала конвертировать.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tracks.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    BackingPanel(store, Modifier.padding(12.dp))
                }
                Text("Загруженные", style = MaterialTheme.typography.titleMedium)
                tracks.forEach { t ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall)
                                val lesson = t.lessonId?.let { ru.staschig.guitar.lessons.Curriculum.byId(it) }
                                Text(
                                    lesson?.let { "Урок ${it.number}: ${it.song.title}" } ?: "без урока",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                if (BackingPlayer.currentFile == store.library.file(t)) BackingPlayer.release()
                                store.library.delete(t)
                            }) { Icon(Icons.Filled.Delete, "Удалить") }
                        }
                    }
                }
            }
        }
    }
}
