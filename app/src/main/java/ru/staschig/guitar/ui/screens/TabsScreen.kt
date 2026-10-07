package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.SavedTab
import ru.staschig.guitar.lessons.Curriculum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(store: AppStore, onOpenUrl: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Табы") })
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(onClick = { onOpenUrl(Curriculum.TABS_LIBRARY_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Открыть библиотеку GuitarMaestro")
                }
                Text(
                    "В браузере нажмите ★, чтобы сохранить таб сюда, или «Прикрепить», если открыли его из урока.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
            if (store.tabs.isEmpty()) {
                item { Text("Сохранённых табов пока нет.") }
            }
            items(store.tabs, key = { it.url + it.lessonId }) { tab ->
                Card(Modifier.fillMaxWidth().clickable { onOpenUrl(tab.url) }) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(tab.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            val lesson = tab.lessonId?.let { Curriculum.byId(it) }
                            if (lesson != null) {
                                Text("${lesson.level.title} · урок ${lesson.number}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        IconButton(onClick = { store.removeTab(tab) }) { Icon(Icons.Filled.Delete, "Удалить") }
                    }
                }
            }
        }
    }
}
