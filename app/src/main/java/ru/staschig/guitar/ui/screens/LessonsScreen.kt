package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Level

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonsScreen(store: AppStore, onOpenLesson: (String) -> Unit, onOpenSettings: () -> Unit) {
    val settings = store.settings
    var level by rememberSaveable { mutableStateOf(settings.level) }
    val lessons = Curriculum.byLevel(level)
    val next = Curriculum.byLevel(settings.level).firstOrNull { it.id !in store.completed }
    val today = store.minutesToday()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Уроки") },
            actions = {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Настройки") }
            },
        )
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Сегодня: $today из ${settings.dailyMinutes} мин", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (today.toFloat() / settings.dailyMinutes).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Серия: ${store.streak()} дн. · Уровень: ${settings.level.title} · Фокус: ${settings.focus.title}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (next != null) {
                            Spacer(Modifier.height(12.dp))
                            Text("Следующий урок:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                next.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable { onOpenLesson(next.id) },
                            )
                        } else {
                            Spacer(Modifier.height(12.dp))
                            Text("Все уроки уровня пройдены — переходите на следующий в настройках 🎉")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Level.entries.forEach {
                        FilterChip(selected = level == it, onClick = { level = it }, label = { Text(it.title) })
                    }
                }
            }
            items(lessons, key = { it.id }) { lesson ->
                val done = lesson.id in store.completed
                Card(Modifier.fillMaxWidth().clickable { onOpenLesson(lesson.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(lesson.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "🎵 ${lesson.song.artist} — ${lesson.song.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
