package ru.staschig.guitar.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.runtime.remember
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.SavedTab
import ru.staschig.guitar.data.TabKind
import ru.staschig.guitar.lessons.Curriculum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    store: AppStore,
    onBack: (() -> Unit)? = null,
    onOpenUrl: (String) -> Unit,
    onOpenDoc: (String) -> Unit,
    onOpenPiece: (String) -> Unit,
    onNewText: () -> Unit,
    onNewTab: () -> Unit = {},
) {
    var addMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "tab"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }
                store.library.importStream(name, bytes.inputStream())
            }.onSuccess { onOpenDoc(it.id) }
                .onFailure { Toast.makeText(context, "Не удалось импортировать: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    if (addMenu) {
        AlertDialog(
            onDismissRequest = { addMenu = false },
            title = { Text("Добавить таб") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddOption("✏️  Создать таб вручную", "Редактор: лады, длительности, приёмы. Можно сохранить как .gp") {
                        addMenu = false; onNewTab()
                    }
                    AddOption("📁  Файл с телефона", "Guitar Pro (.gp, .gp5, .gpx…), MusicXML, PDF, текст") {
                        addMenu = false; importer.launch(arrayOf("*/*"))
                    }
                    AddOption("🌐  Найти на GuitarMaestro", "Откроется сайт; на странице таба нажмите ⬇") {
                        addMenu = false; onOpenUrl(Curriculum.TABS_LIBRARY_URL)
                    }
                    AddOption("📋  Вставить текст таба", "Скопированный откуда-то таб буквами") {
                        addMenu = false; onNewText()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addMenu = false }) { Text("Отмена") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Табы") },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } } },
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                BigButton("＋  Добавить таб", { addMenu = true })
                Text(
                    "Создайте таб сами, загрузите файл Guitar Pro с телефона или сохраните с GuitarMaestro (кнопка ⬇ на странице таба).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            item { Header("Встроенные пьесы — ноты, таб и звук") }
            items(Curriculum.builtInPieces, key = { "p_" + it.asset }) { p ->
                Entry(Icons.Filled.QueueMusic, p.title, "${p.artist} · ${p.level.title}") { onOpenPiece(p.asset) }
            }

            item { Header("Мои табы (офлайн)") }
            val myDocs = store.library.docs.filter { it.kind != TabKind.AUDIO }
            if (myDocs.isEmpty()) {
                item { Text("Пока пусто. Сохраните таб с сайта или импортируйте файл.") }
            }
            items(myDocs.sortedByDescending { it.created }, key = { "d_" + it.id }) { d ->
                val icon = when (d.kind) {
                    TabKind.GP -> Icons.Filled.MusicNote
                    TabKind.PDF -> Icons.Filled.PictureAsPdf
                    else -> Icons.Filled.Article
                }
                val lesson = d.lessonId?.let { Curriculum.byId(it) }
                val subtitle = listOfNotNull(
                    if (store.library.isEditable(d)) "мой таб (редактор)" else d.kind.title,
                    d.artist.ifBlank { null },
                    lesson?.let { "${it.level.title}, урок ${it.number}" },
                ).joinToString(" · ")
                Entry(icon, d.title, subtitle, onDelete = { store.library.delete(d) }) { onOpenDoc(d.id) }
            }

            item { Header("Ссылки") }
            items(store.tabs, key = { "l_" + it.url + it.lessonId }) { tab ->
                val lesson = tab.lessonId?.let { Curriculum.byId(it) }
                Entry(Icons.Filled.Link, tab.title, lesson?.let { "${it.level.title}, урок ${it.number}" } ?: tab.url,
                    onDelete = { store.removeTab(tab) }) { onOpenUrl(tab.url) }
            }
            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Добавить ссылку вручную", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(url, { url = it }, label = { Text("Ссылка") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        OutlinedButton(
                            enabled = url.startsWith("http"),
                            onClick = {
                                store.addTab(SavedTab(title.ifBlank { url }, url.trim()))
                                url = ""; title = ""
                            },
                        ) { Text("Сохранить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Entry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Удалить") }
            else Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun AddOption(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
